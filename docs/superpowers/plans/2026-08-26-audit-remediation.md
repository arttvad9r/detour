# Audit Remediation Implementation Plan

> **For agentic workers:** Implement task-by-task with tests first.

**Goal:** Close the confirmed routing, lifecycle, storage, security, build, UI, and documentation findings from the full Detour audit.

**Architecture:** Preserve the existing small Kotlin/Go design. Make routing fail closed, keep state mutations atomic, put validation at trust boundaries, and make CI build the native artifact it consumes. Prefer focused pure helpers and existing platform APIs over new dependencies.

**Tech Stack:** Kotlin, Android VPN/Compose/DataStore, Go mihomo wrapper, Gradle, Arch Linux development environment, GitHub Actions.

**Spec:** Full audit report delivered 2026-08-26.

## Global Constraints

- Do not expose selected traffic through `DIRECT` when ownership is unknown.
- IPv6 must not bypass the VPN; unsupported IPv6 traffic must be rejected explicitly.
- Imported settings must not connect or restart the VPN without explicit user action.
- External backup input must be bounded and strictly validated.
- Clean checkout CI must build and consume its own native artifacts.
- Add regression tests before production changes where a JVM test is possible.

## Tasks

1. Routing policy: fail-closed unknown UID, shared UID validation, explicit IPv6 rejection, and tests.
2. VPN/native lifecycle: TUN readiness/FD cleanup, cancellation, process death, health retries, and tests.
3. DataStore/backup: stable migration, atomic key mutations, strict bounded import, complete restore, backup rules, and tests.
4. Supply chain/build/docs: CI artifact flow, pinning/checksums/scans, dependency updates, and current documentation.
5. UI: localization, accessibility, saveable state, non-blocking I/O, and warning cleanup.
6. Full verification: unit tests, lint, assemble, native checks, dependency scans, and final review.
