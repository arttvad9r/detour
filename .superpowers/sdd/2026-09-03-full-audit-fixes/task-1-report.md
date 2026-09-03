# Task 1 Report

## Files Changed

- `engine/mihomo/go/engine.go`
- `engine/mihomo/go/engine_test.go`

## Tests Run

- Initial focused test run on the unprepared tree: **FAIL** during compilation because the pinned mihomo source requires the project's `with_gvisor` preparation, and the new helper was not yet implemented.
- Project `engine/mihomo/build.sh` preparation with a temporary no-op `gomobile` wrapper: focused package tests **PASS** (`ok engine 0.159s`); the wrapper intentionally stopped the later AAR validation.
- Prepared-tree focused race tests: **PASS** (`ok engine 1.193s`).
- Prepared-tree `go test -race ./...`: **FAIL** due a data race in the existing AmneziaWG test background goroutine reading mihomo logging state while another test calls `log.SetLevel`; no Task 1 assertion failed.
- `git diff --check`: **PASS**.

## Design Decisions

- Added one mutex around the complete Start/Stop lifecycle, preserving the existing exported Go/JNI function signatures.
- Treat an enabled TUN whose post-ApplyConfig state is disabled as a startup error; readiness is published only after that check.
- Scoped selected-node cache keys by a stable SHA-256 identity of the profile home directory. Empty home directories retain the original `SUBSCRIPTION` key, and non-empty profiles read that key as a migration fallback.
- Kept mihomo cachefile persistence and its existing encryption/storage mechanism unchanged.

## Concerns

- Full race verification remains blocked by the pre-existing mihomo/AmneziaWG background-goroutine race described above. Fixing it would require changes outside the Task 1 file scope.
- The pinned mihomo provider interface exposes no provider close method; runtime cleanup remains delegated to `executor.Shutdown` and mihomo's listener lifecycle.
