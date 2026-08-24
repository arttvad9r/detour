# Testing evidence

## Окружение

- Эмулятор: AVD `triplet`, system-images android-35 google_apis x86_64, API 35, эмулятор NixOS (nixpkgs emulator 37.1.11), headless.
- Сборка: `gradle :app:assembleDebug` под project nix-shell; unit: `:app:testDebugUnitTest` — 25/25 green на момент Task 10.
- VLESS-сервер: Reality + xtls-rprx-vision + tcp (ключ пользователя; в репозиторий и логи не попадает, ниже маскирован).
- ByeDPI v0.17.3 (статический ciadpi из jniLibs), пресет RECOMMENDED.

## Сквозное доказательство маршрутов (Task 10)

Сценарий: Chrome → VPN, YouTube → DPI, остальные приложения вне allow-list TUN.

| Проверка | Результат | Доказательство |
|---|---|---|
| Парсер реального ключа | PASS | Settings показывает «Valid» (uiautomator dump) |
| Подключение одной кнопкой | PASS | Home: `Active`; нотификация foreground с Stop |
| TUN поднят движком | PASS | mihomo.log: `[TUN] Tun adapter listening at: tun1(fd=135)([198.18.0.1/30],[fdfe:…]), stack: gVisor` |
| Chrome egress = сервер VLESS | PASS | ifconfig.me/ip вернул `95.164.120.220` = gethostbyname(server) хоста; uid Chrome = 10145 |
| YouTube через DPI-outbound | PASS | mihomo.log: `match Uid(10166) using DPI` ×14 за сессию; uid YouTube = 10166 |
| VPN-outbound выбор для Chrome | PASS | mihomo.log: `match Uid(10145) using VLESS` |
| Негатив: нет ключа при VPN-приложении | PASS | Failed «A VLESS key is required for VPN-routed apps»; без реконнект-лупа |
| Негатив: мусор в поле ключа | PASS | Инлайн-валидация: «Invalid · Invalid link format» до Save |
| Негатив: невалидный сохранённый ключ | PASS | Failed err_invalid_key (проверено импортом строки-комментария файла ключа) |
| Авиарежим вкл/выкл | INCONCLUSIVE→OK | На эмуляторе default iface eth0 переживает aviation; туннель продолжил работать (9 свежих VLESS-match после цикла). Честная проверка смены Wi-Fi↔LTE — на OnePlus (Task 11) |
| Сеть выкл (svc wifi/data) | N/A | eth0 не управляется svc; connectivity не рвалась |

Замечания к методике:

- Импорт ключа автоматизирован через временный debug-hook в MainActivity
  (`--es import_uri`, force-stop → start). Хук удалён перед коммитом; установленный
  на эмуляторе APK его содержит, что не влияет на продуктовую логику.
- `match Match using DIRECT` ×4 в логе — DNS/фоновые потоки выбранных приложений,
  чей owner-lookup вернул пусто (известная граница getConnectionOwnerUid на
  уже-закрытых сокетах); на маршруты выбранных приложений это не влияет,
  unselected-трафик в TUN не попадает вовсе (allow-list).

## Unit-уровень (накопительно)

| Компонент | Тесты |
|---|---|
| VlessKeyParser | 6/6 (reality+vision happy-path, схема/транспорт/security/pbk reject) |
| ConfigGenerator | 11/11 (золотой YAML целиком, порядок правил QUIC-before-DPI, LAN по API, MATCH,DIRECT последним, без-key профиль) |
| DpiPresets | 4/4 |
| RoutesMapping | 5/5 |

## Осталось до релиза

- OnePlus acceptance (Task 11): реальный DPI-блокирующий провайдер, смена Wi-Fi↔LTE, LAN-negative, перезагрузка устройства, пресеты.
