# Detour Corrective Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the verified VPN, persistence, validation, lifecycle, build, and regression gaps in the current Detour repository without changing the UI design or application id.

**Architecture:** Keep the existing pure Kotlin helpers and DataStore model. Add small pure functions for effective routes, probe selection, validation, backup parsing, auto-connect, and restart reconciliation; make Android service code consume those decisions. Keep IPv4-only TUN fail-closed and use one atomic DataStore edit for restore.

**Tech Stack:** Kotlin/JVM unit tests, Android VpnService, Preferences DataStore, Mihomo YAML, ByeDPI ProcessBuilder, Gradle, GitHub Actions, Arch Linux development environment.

**Spec:** User corrective-pass requirements in the conversation.

## Global Constraints

- Preserve `applicationId = dev.triplet.app` and existing settings compatibility.
- Do not add heavyweight dependencies or redesign Compose UI.
- Do not use empty allow-lists as capture-all.
- Do not claim success before tests, lint, assemble, and available native checks run.

---

### Task 1: Pure routing, IPv4-only TUN, and probe contract

**Files:** `core/Model.kt`, new `vpn/EffectiveRoutes.kt`, `core/ConfigGenerator.kt`, `vpn/HealthCheck.kt`, `vpn/TriVpnService.kt`, related tests.

- [ ] Add tests for empty/removed/mixed routes, shared UID conflicts, IPv4-only config, and probe rules.
- [ ] Implement effective route filtering and stop service before TUN creation when no effective route remains.
- [ ] Remove IPv6 TUN address and IPv6 route/configuration; retain `ipv6: false` and explicit IPv4 routes.
- [ ] Generate dedicated probe routing/inbounds or direct outbound checks so VPN probes use VLESS and DPI probes use SOCKS, never `MATCH,DIRECT`.
- [ ] Wire service health checks to configured route types and clean up engine/TUN on failure.

### Task 2: Input validation, YAML serialization, DNS, and DPI policy

**Files:** `core/VlessKeyParser.kt`, `core/ConfigGenerator.kt`, `core/DnsOptions.kt`, `core/DpiPresets.kt`, `core/DpiBackend.kt`, UI validation call sites, tests.

- [ ] Add hostile-input tests and strict VLESS field validation.
- [ ] Implement one YAML scalar serializer rejecting control characters and safely quoting external values.
- [ ] Define one DNS validator for IPv4, supported IPv6, and HTTPS DoH; use it in UI/import/runtime config and generated DNS hijack settings.
- [ ] Add whitelist-based custom ByeDPI strategy parsing that cannot override listener/process/file options.
- [ ] Add tests for accepted strategy and forbidden service-level arguments.

### Task 3: Backup v2 and DataStore correctness

**Files:** `core/SettingsBackup.kt`, `ui/BackupScreen.kt`, `data/RoutesStore.kt`, `core/VlessKeys.kt`, tests.

- [ ] Define version-2 JSON containing all persisted user settings, full VLESS list and active id, with app marker.
- [ ] Parse and validate the complete backup before one atomic DataStore edit; replace routes rather than merge them.
- [ ] Preserve v1 migration and reject malformed/foreign/unknown backups without partial writes.
- [ ] Safely parse unknown routes and corrupted key storage; preserve legacy fallback policy explicitly.
- [ ] Fix key deletion semantics and test inactive, active, only, and nonexistent ids.

### Task 4: Consent, auto-connect, and lifecycle

**Files:** `tile/DetourTile.kt`, `vpn/VpnController.kt`, new transparent consent Activity if needed, `MainActivity.kt`, new pure lifecycle/coordinator helper, `DpiBackend.kt`, tests.

- [ ] Add consent Activity that starts the service after `RESULT_OK`; tile uses it only when permission is absent.
- [ ] Add pure `canAutoConnect` based on effective routes, active VLESS readiness, and existing permission; do not silently launch consent.
- [ ] Coalesce restart requests into latest desired state with STOP priority and no queued work after destruction.
- [ ] Stop ByeDPI with destroy/wait/force/wait and reject dead processes during port wait.

### Task 5: Presentation, secrets, cleanup, and documentation

**Files:** `HomeScreen.kt`, `AndroidManifest.xml`, cleanup candidates, `README.md`, `docs/pins.md`, `docs/testing.md`, design docs, tests.

- [ ] Base home protocol on effective routes, not key existence; update presentation tests.
- [ ] Disable platform backup (or explicitly exclude secrets) and audit logs for credentials/generated YAML.
- [ ] Remove only demonstrably unused code/resources and update `AppRouteOrdering` signature if unused parameter is confirmed.
- [ ] Document actual IPv4-only/DNS/DPI/backup/build/test behavior and limitations without rewriting historical evidence.

### Task 6: Reproducible native builds and CI

**Files:** native build scripts, `.github/workflows/*`, README/docs.

- [ ] Pin exact Mihomo and ByeDPI revisions, reset cleanly, and fail hard on patch/layout mismatch.
- [ ] Keep Java, Android SDK/NDK, Go and native helper requirements explicit in current development instructions and CI.
- [ ] Add non-fake push/PR workflow for unit tests, lint, assembleDebug, and a separately cacheable native check.

### Task 7: Verification

- [ ] Run `./gradlew :app:testDebugUnitTest`.
- [ ] Run `./gradlew :app:lintDebug`.
- [ ] Run `./gradlew :app:assembleDebug`.
- [ ] Run clean/pinned native checks available in the environment.
- [ ] Review git diff/status and report fixed, already-fixed, remaining, commands/results, files, and real risks.
