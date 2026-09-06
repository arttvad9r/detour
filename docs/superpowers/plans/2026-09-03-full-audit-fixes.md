# Full Audit Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Исправить подтверждённые runtime, security, delivery и UI проблемы аудита в ветке `fix/full-audit`.

**Architecture:** Сначала укрепить границы Go engine lifecycle и subscription data flow, затем исправить CI/release trust boundaries, затем локально сделать Compose layouts adaptive и удалить только доказанно неиспользуемый код. Существующие API и encrypted persistence сохраняются.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation 3, Go, Mihomo, Gradle Kotlin DSL, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-03-full-audit-fixes-design.md`

## Global Constraints

- No new dependency unless an existing API cannot provide the behavior.
- Preserve validation, fail-closed routing, encrypted credentials, and component permissions.
- Changes stay on branch `fix/full-audit`.
- Do not remove persisted-backup compatibility or external gomobile bindings.

---

### Task 1: Runtime Regression Tests and Synchronization

**Files:**
- Modify: `engine/mihomo/go/engine_test.go`
- Modify: `engine/mihomo/go/engine.go`
- Modify: `engine/mihomo/go/jni.go` if JNI lifecycle exposure requires it

**Interfaces:**
- `Engine.Start(config, fd)` must publish ready only after TUN creation succeeds.
- `Engine.Stop()` must serialize against Start and close the active runtime.
- Profile selection must not use one global key for all profiles.

- [ ] **Step 1: Write failing tests** for failed TUN startup readiness, concurrent start/stop serialization, and profile-specific selected-node persistence.
- [ ] **Step 2: Run focused Go tests** with the project’s `with_gvisor` preparation and confirm the new tests fail for the current implementation.
- [ ] **Step 3: Implement the smallest lifecycle guard**: protect native runtime state with the existing mutex, return/record ApplyConfig failure, verify TUN state before ready, and close old providers during replacement/shutdown.
- [ ] **Step 4: Implement profile-scoped selection keys** while preserving existing single-profile persisted values as a migration fallback.
- [ ] **Step 5: Run focused Go tests and `go test -race ./...`.

### Task 2: Subscription Materialization and Retry Behavior

**Files:**
- Modify: `app/src/main/java/dev/detour/app/core/SubscriptionProviderMaterializer.kt`
- Modify: `app/src/main/java/dev/detour/app/core/ConfigGenerator.kt`
- Modify: `app/src/main/java/dev/detour/app/DetourApp.kt`
- Modify: `app/src/main/java/dev/detour/app/ui/SubscriptionRuntimeSection.kt`
- Modify: `app/src/main/java/dev/detour/app/ui/SubscriptionRuntimeViewModel.kt`
- Test: existing core and runtime tests under `app/src/test` and `app/src/androidTest`

**Interfaces:**
- Generated config must reference the normalized app-private provider file.
- Failed automatic node selection must settle into an error state, not immediately relaunch itself.

- [ ] **Step 1: Add a test proving generated subscription config uses the materialized provider and a test proving selection failure does not retry without user action.**
- [ ] **Step 2: Run the focused tests and confirm failure.**
- [ ] **Step 3: Connect materialization at the existing config-generation boundary, preserve cleanup, and replace the self-triggering effect with explicit retry state/action.**
- [ ] **Step 4: Run focused Kotlin tests and relevant instrumentation tests.**

### Task 3: Network and Backup Security

**Files:**
- Modify: `engine/mihomo/go/subscription_prepare.go`
- Modify: `app/src/main/java/dev/detour/app/core/WarpProfile.kt`
- Modify: `app/src/main/java/dev/detour/app/core/WarpConfigImporter.kt`
- Modify: `app/src/main/java/dev/detour/app/BackupDocumentIo.kt`
- Test: Go subscription tests and Android backup/WARP tests

- [ ] **Step 1: Add redirect-origin and malformed-WARP regression tests.**
- [ ] **Step 2: Run them and confirm the current behavior fails.**
- [ ] **Step 3: Reject cross-origin redirects and validate IP/CIDR/key/Amnezia ranges at import time; preserve the existing 1 MiB bounded document read.**
- [ ] **Step 4: Run focused tests and Go race tests.**

### Task 4: CI, Release, and Native Toolchain

**Files:**
- Modify: `.github/workflows/android.yml`
- Modify: `.github/workflows/release.yml`
- Modify: `build.gradle.kts`
- Modify: `engine/vulnscan.sh`

- [ ] **Step 1: Add CI assertions that dependency verification files are unchanged during builds and that release requires all test/build gates.**
- [ ] **Step 2: Run workflow YAML/static checks available locally and inspect the diff for least privilege.**
- [ ] **Step 3: Remove automatic trust mutation, use non-clobbering artifact publication, validate signing certificate before upload, include NDK/gomobile/tool inputs in native task fingerprints, and make missing `govulncheck` fail with a clear actionable error.**
- [ ] **Step 4: Run Gradle strict verification, lint, debug/release builds, R8 analysis, and the vulnerability script.**

### Task 5: Compose Accessibility and Adaptive Layout

**Files:**
- Modify: `app/src/main/java/dev/detour/app/ui/HomeScreen.kt`
- Modify: `app/src/main/java/dev/detour/app/ui/VlessKeyScreen.kt`
- Modify: `app/src/main/java/dev/detour/app/ui/NavigationRow.kt`
- Modify: `app/src/main/java/dev/detour/app/ui/AppsScreen.kt`
- Modify: `app/src/main/java/dev/detour/app/ui/Theme.kt`
- Test: `app/src/androidTest/java/dev/detour/app/ui/FontScaleLayoutTest.kt` and accessibility smoke tests

- [ ] **Step 1: Add tests for compact-height scrolling, 200% text visibility, descriptive profile action labels, and selected-state contrast.**
- [ ] **Step 2: Run focused instrumentation tests and confirm missing behavior.**
- [ ] **Step 3: Add scroll containers/insets without changing the information hierarchy, use semantic labels containing the profile name, adjust only failing color tokens, and avoid persisting sensitive text beyond the existing editor lifecycle.**
- [ ] **Step 4: Run all connected Android tests with screen awake and inspect a fresh screenshot/layout dump.**

### Task 6: Dead Code and Resource Cleanup

**Files:**
- Delete only symbols/files confirmed unused by repository-wide usage search.
- Modify: resource files containing confirmed unused strings.
- Preserve: `native-engine.keep`, backup compatibility parsers, and gomobile-generated binding APIs.

- [ ] **Step 1: Re-run usage searches for each candidate and record the exact zero callers.**
- [ ] **Step 2: Delete candidates and remove only their now-unused resources/imports.**
- [ ] **Step 3: Run compilation, lint, R8 and Go tests to catch reflection/binding mistakes.**

### Task 7: Full Verification and Review

**Files:**
- No production changes unless verification exposes a regression.

- [ ] **Step 1: Run the complete Gradle verification command with strict dependency verification.
- [ ] **Step 2: Run `go test ./...`, `go test -race ./...`, and `go mod verify` in the prepared engine tree.
- [ ] **Step 3: Run connected Android tests and inspect rendered home/editor states on the available device.
- [ ] **Step 4: Run `git diff --check`, inspect status/diff, and report any unavailable checks such as missing `govulncheck`.
