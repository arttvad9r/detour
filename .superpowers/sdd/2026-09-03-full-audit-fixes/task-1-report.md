# Task 1 Report

## Files Changed

- `engine/mihomo/go/engine.go`
- `engine/mihomo/go/engine_test.go`
- `engine/mihomo/build.sh`
- `engine/mihomo/go/subscription_prepare.go`

## Tests Run

- Initial focused test run on the unprepared tree: **FAIL** during compilation because the pinned mihomo source requires the project's `with_gvisor` preparation, and the new helper was not yet implemented.
- Project `engine/mihomo/build.sh` preparation with a temporary no-op `gomobile` wrapper: focused package tests **PASS** (`ok engine 0.159s`); the wrapper intentionally stopped the later AAR validation.
- Prepared-tree focused race tests: **PASS** (`ok engine 1.193s`).
- Prepared-tree `go test -race ./...`: **FAIL** due a data race in the existing AmneziaWG test background goroutine reading mihomo logging state while another test calls `log.SetLevel`; no Task 1 assertion failed.
- `git diff --check`: **PASS**.

- Final `go test -race ./...`: **FAIL** (`engine 0.374s`) on the same pre-existing AmneziaWG goroutine/logging race in `TestStopReleasesCustomProbeListener`.

## Round 3

- Replaced the timing-based overlap test with two barrier-controlled goroutines. Both signal intent before the lifecycle mutex is released; all assertions run after the mutex is no longer held, and cleanup releases it if setup fails.
- `go test -tags with_gvisor ./... -run 'TestConcurrentStartAndStopLeaveEngineStopped'`: **PASS** (`ok engine 0.031s`).
- `go test -race -tags with_gvisor ./... -run 'TestConcurrentStartAndStopLeaveEngineStopped'`: **PASS** (`ok engine 1.157s`).
- `go test -tags with_gvisor ./...`: **PASS** (`ok engine 0.177s`).
- `go test -race ./...`: **FAIL** (`engine 0.311s`) on the pre-existing AmneziaWG background-goroutine/logging race.

## Scoped Reviewer Round 2

- Failed replacement now tears down the newly partial runtime on both ApplyConfig and TUN failure paths; config parse failures occur before teardown so the existing runtime is preserved.
- `HealthCheckSubscriptionProvider`, active latency inspection, and catalog latency now synchronize runtime access with `runtimeMu`; the internal locked helper avoids recursive locking.
- Stop traversal is covered with a provider inserted into mihomo's active provider map, and the test restores that global map.
- The overlap test signals goroutine entry before releasing `runtimeMu` and performs no fatal assertion while holding the lock.

## Round 2 Verification

- RED: prepared build test failed with `undefined: resolveSubscriptionCache` before the prior-round helper existed.
- `PATH=/tmp/opencode/fakebin:$PATH ./build.sh`: Go test stage **PASS** (`ok engine 0.180s`); temporary gomobile wrapper intentionally failed only the later AAR validation.
- `go test -tags with_gvisor ./... -run 'Test(StartDoesNotReportReadyWhenTunCreationFails|FailedReplacementLeavesRuntimeStopped|ConcurrentStartAndStopLeaveEngineStopped|SubscriptionSelectionIsScopedToProfile|SubscriptionSelectionUsesLegacyKeyAsMigrationFallback|ProviderCleanupCallsPinnedCloseHook|StopTraversesAndClosesProviders|SubscriptionLatency)'`: **PASS** (`ok engine 0.174s`).
- `go test -race -tags with_gvisor ./... -run 'Test(StartDoesNotReportReadyWhenTunCreationFails|FailedReplacementLeavesRuntimeStopped|ConcurrentStartAndStopLeaveEngineStopped|SubscriptionSelectionIsScopedToProfile|SubscriptionSelectionUsesLegacyKeyAsMigrationFallback|ProviderCleanupCallsPinnedCloseHook|StopTraversesAndClosesProviders|SubscriptionLatency)'`: **PASS** (`ok engine 1.367s`).
- Prepared-tree `go test -race ./...`: **FAIL** on the pre-existing AmneziaWG background-goroutine/logging race; no Task 1 test failure was reported.
- `git diff --check`: **PASS**.

## Reviewer Follow-up

- `engine/mihomo/go/engine.go` now keeps the prior runtime intact when config parsing fails, tears down the prior runtime before replacement, propagates prepared `ApplyConfig` failures, synchronizes all runtime-facing APIs, and closes proxy/rule providers through the pinned optional `Close() error` hook.
- `engine/mihomo/build.sh` makes the pinned void dispatcher return recovered apply panics as errors while preserving existing callers.
- `engine/mihomo/go/engine_test.go` now forces Start/Stop overlap while the lifecycle mutex is held, covers legacy selection migration, verifies provider cleanup, and restores profile/home state.

## Design Decisions

- Added one mutex around the complete Start/Stop lifecycle, preserving the existing exported Go/JNI function signatures.
- Treat an enabled TUN whose post-ApplyConfig state is disabled as a startup error; readiness is published only after that check.
- Scoped selected-node cache keys by a stable SHA-256 identity of the profile home directory. Empty home directories retain the original `SUBSCRIPTION` key, and non-empty profiles read that key as a migration fallback.
- Kept mihomo cachefile persistence and its existing encryption/storage mechanism unchanged.

## Concerns

- Full race verification remains blocked by the pre-existing mihomo/AmneziaWG background-goroutine race described above. Fixing it would require changes outside the Task 1 file scope.
- The pinned provider interface itself omits `Close`, but pinned concrete providers expose the optional `Close() error` hook; the bridge now invokes it when available.

## Reviewer Fixes

- `engine/mihomo/go/engine.go` now tears down the old runtime before applying a replacement, propagates prepared `ApplyConfig` errors, closes proxy and rule providers through the pinned `Close() error` hook, and serializes runtime-dependent public APIs.
- `engine/mihomo/build.sh` patches the pinned void `ApplyConfig` dispatcher to return recovered apply panics as errors.
- Tests now force lifecycle overlap, cover legacy selection migration, verify provider cleanup, and restore global profile/home state.

## Reviewer Fix Verification

- RED: prepared build test failed with `undefined: resolveSubscriptionCache` before the production helper was added.
- `PATH=/tmp/opencode/fakebin:$PATH ./build.sh`: Go test stage **PASS** (`ok engine 0.164s`); temporary gomobile wrapper intentionally failed only the later AAR validation.
- `go test -tags with_gvisor ./... -run 'Test(StartDoesNotReportReadyWhenTunCreationFails|ConcurrentStartAndStopLeaveEngineStopped|SubscriptionSelectionIsScopedToProfile|SubscriptionSelectionUsesLegacyKeyAsMigrationFallback|ProviderCleanupCallsPinnedCloseHook)'`: **PASS** (`ok engine 0.057s`).
- `go test -race -tags with_gvisor ./... -run 'Test(StartDoesNotReportReadyWhenTunCreationFails|ConcurrentStartAndStopLeaveEngineStopped|SubscriptionSelectionIsScopedToProfile|SubscriptionSelectionUsesLegacyKeyAsMigrationFallback|ProviderCleanupCallsPinnedCloseHook)'`: **PASS** (`ok engine 1.190s`).
- Full prepared-tree `go test -race ./...`: **FAIL** on the same pre-existing AmneziaWG background-goroutine/logging race reported above.
- `git diff --check`: **PASS**.

## Round 4

- Replaced the pre-call overlap barriers with a mutex-acquisition hook. The test now blocks immediately after `Start` owns `runtimeMu`, starts `Stop`, and asserts the observed acquisition order is `Start` then `Stop` before joining both goroutines.
- Cleanup releases the blocked hook through `sync.Once`, waits for both lifecycle goroutines, and only then clears the test hook.
- Prepared-tree focused concurrency test: **PASS** (`go test ./... -run '^TestConcurrentStartAndStopLeaveEngineStopped$' -count=10`).
- Prepared-tree focused concurrency race test: **PASS** (`go test -race ./... -run '^TestConcurrentStartAndStopLeaveEngineStopped$' -count=10`).
- Prepared-tree Task 1 focused normal and race selectors: **PASS** (`ok engine 0.169s` and `ok engine 1.387s`).

## Round 5

- Fixed the test cleanup deadlock by separating goroutine completion channels from the consumed Start result and Stop completion events.
- Increased the acquisition channel capacity and consumed the third `Stop` hook signal from the explicit final stop.
- `GOFLAGS=-mod=mod go test -tags with_gvisor ./... -run 'Test(StartDoesNotReportReadyWhenTunCreationFails|FailedReplacementLeavesRuntimeStopped|ConcurrentStartAndStopLeaveEngineStopped|SubscriptionSelectionIsScopedToProfile|SubscriptionSelectionUsesLegacyKeyAsMigrationFallback|ProviderCleanupCallsPinnedCloseHook|StopTraversesAndClosesProviders|SubscriptionLatency)'`: **PASS** (`ok engine 0.172s`).
- `GOFLAGS=-mod=mod go test -race -tags with_gvisor ./... -run 'Test(StartDoesNotReportReadyWhenTunCreationFails|FailedReplacementLeavesRuntimeStopped|ConcurrentStartAndStopLeaveEngineStopped|SubscriptionSelectionIsScopedToProfile|SubscriptionSelectionUsesLegacyKeyAsMigrationFallback|ProviderCleanupCallsPinnedCloseHook|StopTraversesAndClosesProviders|SubscriptionLatency)'`: **PASS** (`ok engine 1.382s`).
