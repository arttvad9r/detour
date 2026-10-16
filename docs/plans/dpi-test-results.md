# DPI strategy selection and diagnostics

## Requirements

- Reuse the pinned 60-strategy corpus and authenticated HTTPS runner. No new transport, listener options or untrusted host inputs.
- Default to three attempts per host. Explain ranking and the scope of HTTPS checks; do not promise app/video playback or future network stability.
- Recommend the best completed result only if it has successful checks. Never label an all-zero run as best or allow applying a zero-success/incomplete/backend-failed strategy. Partial candidates remain explicitly partial.
- Show aggregate results by selected catalog service group, retain them in history, and keep old v1 history readable without inventing missing service details.
- Apply the candidate in one DataStore transaction, restart at most once, expose saving/success/error, preserve explicit user action.
- Update progress at strategy start and backend failure as well as host completion, with monotonic host counts.
- Export a user-selected JSON file for completed, failed, cancelled or VPN-contaminated attempts. Capture the exact report before opening the Android picker. Only app/API versions, catalog service IDs, trusted reference indexes or a generic custom marker, numeric configuration/results and fixed outcome values are allowed. Exclude timestamps, run IDs, URLs, IP addresses, custom commands, logs, VPN credentials and app inventories.

## Validation

Focused policy, grouping, history compatibility and malicious-input export tests; native recommendation/actions/large-font checks; strict JVM/lint/build gates and Android 16/17 instrumentation. Inspect native RU light/dark and 200% font output. Perform a small real HTTPS test on an emulator; provider-specific blocking efficacy requires the user's network and is not established by emulator results.

## Result (2026-10-16)

Strict JVM tests, debug/release lint, debug/release APK builds, instrumentation assembly and baseline-profile assembly passed. All instrumentation passed on leased Android 16 and Android 17 emulators. The first Android 16 run used the snapshot's 200% system font and failed existing baseline layout checks; the final full run used 100%, with focused 200% tests and screenshots verified separately.

Manual Android 17 HTTPS smoke tested one catalog strategy against 13 YouTube addresses, three attempts each (39 checks). Partial results were shown explicitly; applying and SAF JSON export succeeded. Exported metadata and aggregate results were inspected. Android 16 cancellation was exported as `cancelled` with no stale results. Native RU light/dark and 200% font action screenshots were inspected. Provider-specific bypass effectiveness and video playback remain unverified. The local Android 17 image uses 4 KB pages; hosted 16 KB runtime validation remains a release gate. No engine source or dependencies changed.
