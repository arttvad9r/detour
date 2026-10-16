# App route details

## Behavior

- Tapping an app identity in the route list opens its detail screen. Inline route assignment and bulk selection retain their existing behavior.
- Show the package, saved Direct/VPN/DPI assignment and a plain-language path. DPI does not conceal the user's IP address.
- Show configuration checks separately from Detour's current Idle/Starting/Active/Failed connection state. Never claim per-app live traffic, runtime route confirmation or internet reachability.
- Reuse the service's effective-route resolver. Explain excluded own UID, removed apps, shared-UID rejection, tunnel-wide conflicts, missing selected VPN profiles and invalid custom DPI settings. Direct remains outside Detour.
- Provide existing-screen actions for profiles, DPI settings/testing, connection and returning to app routes. Do not change assignments, start the VPN or select profiles on entry.
- Refresh package ownership on resume and after settings changes. Keep credentials and raw engine errors out of this screen.

## Verification

Focused JVM policy tests and native UI tests cover blocked prerequisites, Direct independence, own/shared UID, removal, active-state wording and action reachability at 200% font. Run the strict JVM/lint/build gate and Android 16/17 instrumentation; inspect native Russian screenshots in light/dark themes. Network transport behavior is unchanged.

## Result (2026-10-16)

Implemented without transport, listener, routing-engine or dependency changes. Strict JVM, debug/release lint and builds, instrumentation assembly and baseline-profile assembly passed. Android 16 and Android 17 instrumentation passed. Native Russian light/dark screenshots and 200% font scrolling were inspected. Manual Android 16 smoke covered Direct, DPI configuration, explicit VPN consent, Active and disconnect; the detail screen followed the real connection state. No external VPN server or app traffic throughput/reachability was tested. The local Android 17 emulator is a 4 KB image; this does not replace the hosted 16 KB runtime gate.
