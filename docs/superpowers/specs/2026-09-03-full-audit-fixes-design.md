# Full Audit Fixes Design

**Goal:** Исправить подтверждённые проблемы аудита проекта Detour в отдельной ветке без изменения публичного поведения сверх необходимого.

**Scope:** VPN runtime и Go bridge, subscription processing, backup/credential privacy, CI/release verification, Compose accessibility/adaptive layout и подтверждённый dead code.

## Architecture

Runtime lifecycle будет сериализован одной блокировкой на стороне Go bridge; остановка должна закрывать старые providers до замены глобального runtime. `Start` будет считать engine готовым только после успешного создания TUN, а production subscription flow будет использовать нормализованный app-private file provider.

Security fixes сохранят текущую модель хранения credentials, но не будут расширять зависимости: redirects ограничиваются исходным origin, dependency verification перестаёт самообновляться в CI, а release публикуется только после проверяемых gates и не перезаписывает существующие assets.

UI исправления будут локальными: безопасные insets, единый scroll container, перенос/доступность текста на больших размерах, контраст и описательные accessibility semantics. Не будет добавляться новый дизайн-системный слой.

## Implementation Blocks

1. Runtime: add focused regression tests, lifecycle synchronization, provider cleanup, TUN readiness, per-profile selection and production materializer installation.
2. Security and delivery: add redirect-origin coverage, remove automatic checksum trust mutation, harden release ordering/assets/signing checks, and declare native toolchain inputs.
3. UI: make Home/editor/dialog layouts resilient to compact height and large font, correct semantics/contrast, and protect credential drafts where practical.
4. Cleanup: remove only symbols/resources confirmed unused and replace source-rewriter duplication with checked-in patches where behavior is unchanged.

## Verification

Each behavioral change gets a focused unit or instrumentation regression test before implementation. The final gate is strict dependency verification, unit tests, debug/release lint, debug/release builds, R8 analysis, Go tests with `with_gvisor`, and connected Android tests on the available device. Vulnerability scanning is run when `govulncheck` is available; otherwise the missing tool is reported explicitly.

## Constraints

- No new dependency unless an existing API cannot provide the behavior.
- Preserve the existing branch and worktree; all changes are confined to `fix/full-audit`.
- Do not weaken validation, fail-closed routing, credential encryption at rest, or Android component permissions.
- Do not remove compatibility code that is exercised by persisted backup formats or external native bindings.
