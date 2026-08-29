# Testing evidence

## Окружение

- Текущая среда разработки: Arch Linux.
- Android toolchain contract: JDK 17, platform/target 36, build-tools 36.0.0, NDK 28.0.13004108.
- Native engine release/CI toolchain: Go 1.26.7, gomobile, govulncheck.
- Automated verification: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` и `bash engine/vulnscan.sh`.
- Device evidence ниже историческое, если явно не указано обратное.
- VLESS-сервер: Reality + xtls-rprx-vision + tcp; секретные данные в репозиторий и логи не попадают.
- ByeDPI v0.17.3 (dynamic bionic ciadpi из jniLibs), preset RECOMMENDED.
- Motion durations are time-based rather than frame-count-based. On Android 15+ the app keeps platform touch boost/ARR enabled and requests Compose `High` only for bounded navigation, scroll/fling, list-reorder, and expand/collapse motion; static UI returns to `Default` instead of pinning a concrete 120 Hz mode.

## Instrumented

С подключённым эмулятором/устройством:

```bash
ANDROID_SERIAL=emulator-XXXX ./gradlew :app:connectedDebugAndroidTest
```

Исторически подтверждено:

| Класс | Тесты |
|---|---|
| MainActivitySmokeTest | 2/2 (Connect, вход в настройки) |
| RoutesStoreInstrumentedTest | 3/3 (add/update/delete, duplicate id reject, import disables auto-connect) |

## Engine / supply-chain gate

```bash
bash engine/vulnscan.sh
```

- `go test -tags with_gvisor ./...` запускает checked-in engine tests против точного patched mihomo source tree, используемого для AAR.
- `govulncheck -tags with_gvisor ./...` проверяет reachable vulnerabilities в source call graph.
- `govulncheck -mode binary` проверяет каждый shipped `libgojni.so` внутри `engine.aar` (arm64-v8a и x86_64), включая фактически упакованный Go runtime/modules/symbols.

## Unit-уровень (накопительно)

Счётчики соответствуют фактическим текущим regression-наборам.

| Компонент | Тесты |
|---|---|
| VlessKeyParser + fingerprints | 10/10 (8 parser cases + 2 fingerprint compatibility cases) |
| VlessKeys storage | 11/11 (legacy migration, strict JSON, explicit reselection after delete, corrupt-storage fail-closed) |
| ConfigGenerator | 16/16 (15 основных cases + отдельный API 29–32 LAN-before-UID regression) |
| DnsValidation | 3/3 (IP/HTTPS validation, DoH bootstrap selection, bootstrap emission) |
| DNS / backup persistence | 16/16 (5 DnsOptions + 11 SettingsBackup cases) |
| DpiPresets | 4/4 |
| RoutesMapping | 8/8 (включая corrupt WARP/VLESS fail-closed) |
| Theme transition policy | 2/2 (light↔dark snap; same-mode animation retained) |
| Status theme policy | 1/1 (Failed остаётся на neutral surface во всех темах; error используется только семантически) |
| Profile tunnel policy | 4/4 (inactive mutation = none; active edit/replace = restart; active delete = stop) |

## Сквозное доказательство маршрутов

Исторический device scenario: Chrome → VPN, YouTube → DPI, остальные приложения вне allow-list TUN.

| Проверка | Результат | Доказательство |
|---|---|---|
| VLESS parser | PASS | валидный Reality/Vision link принят до подключения |
| Подключение одной кнопкой | PASS | Home `Active`, foreground notification со Stop |
| TUN поднят движком | PASS | mihomo log показывает gVisor TUN на fake-IP gateway `/30` |
| Chrome → VLESS | PASS | egress совпал с адресом VLESS server; UID rule matched VLESS |
| YouTube → DPI | PASS | mihomo log показывает UID match через DPI |
| Нет VLESS при VPN-route | PASS | fail-closed без reconnect loop |
| Невалидный VLESS | PASS | inline validation до Save |
| Wi-Fi ↔ LTE | PASS на OnePlus | network monitor пересоздаёт tunnel, routes сохраняются |
| Secrets in logs | PASS | uuid/VLESS URI/public key не обнаружены в engine/service logs |

## Найденные и исправленные дефекты

1. AppsScreen на узких экранах: route controls вынесены из сжимающей строки имени.
2. VPN consent: после `RESULT_OK` подключение продолжается без второго тапа.
3. Network monitor: VPN network игнорируется, restart storm устранён.
4. ByeDPI: dynamic bionic build вместо неработающего static DNS path.
5. IPv6: selected traffic захватывается и явно отклоняется при неподдерживаемом маршруте.
6. ByeDPI process output: исключена блокировка на заполненном stdout/stderr pipe.
7. RECOMMENDED preset закреплён tuned ladder + setup timeout.
8. Android fake-IP TUN gateway восстановлен до `/30`.
9. Backup import поверх Active останавливает старый tunnel и не включает auto-connect.
10. Home/Settings/Tile используют effective routes, а не stale persisted summaries.
11. VPN allow-list перестраивается после install/uninstall и защищён от UID reuse.
12. API 29–32 LAN REJECT идёт до UID→VLESS/DPI rules; есть regression test порядка.
13. Corrupt WARP/VLESS storage fail-closed и не выбирает другой endpoint неявно.
14. Light↔dark transition не интерполирует foreground/background через низкий contrast.
15. Изменение/удаление невыбранного профиля не рвёт tunnel; active delete делает Stop.
16. Backup JSON serialization/parsing выполняется вне main thread и покрыта error handling.
17. Quick Settings tile использует VPN/lock icon и app resource label; stale strings удалены.
18. Full-screen navigation больше не alpha-crossfade'ит два destination одновременно: incoming screen движется поверх непрозрачного предыдущего, поэтому Settings/Home/Routes/DPI/DNS/Theme не просвечивают друг через друга.
19. Failed-state Home больше не превращает `errorSoft` в почти непрозрачный red/pink fill; карточка остаётся neutral surface во всех темах, error остаётся в title/border, а дублирующий Retry внутри карточки удалён.
20. Motion/refresh policy больше не предполагает фиксированные 60 Гц и не пинит 120 Гц: Android 15+ ARR и touch boost остаются включены, `High` запрашивается только пока реально движутся navigation/scroll/list/expand surfaces, после чего vote возвращается в `Default`.

## Current automated/device limitations

- GitHub Actions проверяет JVM tests, lint, debug APK assembly, patched-engine Go tests и source/binary vulnerability scans.
- `connectedDebugAndroidTest` требует отдельный эмулятор/устройство и не входит в CI job.
- Частота дисплея и фактический выбор ARR зависят от устройства/Android/OEM и не могут быть подтверждены JVM/CI; frame-rate requests являются hints для системного scheduler, а не гарантией конкретных 120 Гц.
- OnePlus/AVD evidence выше историческое; после крупных routing/native changes нужен свежий device smoke на текущем head.
- Current policy captures IPv6 in the TUN and explicitly rejects it, preventing selected applications from bypassing the VPN over IPv6.
- DNS accepts only IP literals or HTTPS DoH URLs and is emitted through mihomo DNS hijack for routed applications.

## Fresh device smoke checklist

- VPN connect/routing после TUN `/30`.
- Backup import во время Active должен остановить текущий tunnel без auto-connect.
- Удаление active VLESS/WARP должно завершать session через Stop; inactive edit/delete не должны рвать tunnel.
- Home/Settings/Tile должны пересчитать effective routes после установки/удаления приложения.
- API 29–32: LAN destinations не должны попадать в VLESS/DPI outbound.
- Навигация Home ↔ Settings ↔ дочерние экраны не должна показывать два полноэкранных UI одновременно во время transition.
- Failed-state проверить в Catppuccin Latte/Mocha, Gruvbox Dark и Dracula: neutral card surface, error title/border, без сплошной красной/розовой заливки и без второго Retry внутри карточки.
- На 120 Гц устройстве включить системный refresh-rate overlay или записать FrameTimeline/Perfetto: navigation и active scroll/fling должны получать high-refresh residency, а статичные экраны после завершения motion должны иметь возможность вернуться к более низкой adaptive частоте.
- Сравнить 60/120 Гц: физическая длительность navigation/segment/switch animation должна оставаться одинаковой; на 120 Гц увеличивается число кадров, а не скорость transition.
