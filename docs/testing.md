# Testing evidence

## Окружение

- Эмулятор: AVD `triplet`, system-images android-35 google_apis x86_64, API 35, эмулятор NixOS (nixpkgs emulator 37.1.11), headless.
- Automated verification uses the pinned Nix flake: `nix develop -c ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` and `nix develop -c bash engine/vulnscan.sh`. Device evidence below is historical evidence, not a fresh run.
- VLESS-сервер: Reality + xtls-rprx-vision + tcp (ключ пользователя; в репозиторий и логи не попадает, ниже маскирован).
- ByeDPI v0.17.3 (dynamic bionic ciadpi из jniLibs), пресет RECOMMENDED.

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
- `match Match using REJECT` ×4 в логе — неизвестные потоки выбранных приложений
  не обходят туннель,
  чей owner-lookup вернул пусто (известная граница getConnectionOwnerUid на
  уже-закрытых сокетах); на маршруты выбранных приложений это не влияет,
  unselected-трафик в TUN не попадает вовсе (allow-list).

## Instrumented (эмулятор, Android 15 / API 35)

`ANDROID_SERIAL=emulator-XXXX nix develop -c ./gradlew :app:connectedDebugAndroidTest`

| Класс | Тесты |
|---|---|
| MainActivitySmokeTest | 2/2 (кнопка Connect, вход в настройки) |
| RoutesStoreInstrumentedTest | 3/3 (add/update/delete, дубликат id отклоняется, импорт не включает auto-connect) |

## Engine / supply-chain gate

`nix develop -c bash engine/vulnscan.sh`

- `go test -tags with_gvisor ./...` запускает checked-in engine tests против точного patched mihomo source tree, который используется для AAR.
- `govulncheck -tags with_gvisor ./...` проверяет reachable vulnerabilities в source call graph.
- Затем `govulncheck -mode binary` запускается для каждого shipped `libgojni.so` внутри `engine.aar` (arm64-v8a и x86_64), поэтому проверяется фактически упакованный Go runtime/modules/symbols.

## Unit-уровень (накопительно)

| Компонент | Тесты |
|---|---|
| VlessKeyParser | 6/6 (reality+vision happy-path, схема/транспорт/security/pbk reject) |
| ConfigGenerator | 15/15 (golden YAML, VLESS/WARP/AmneziaWG schema, IPv6 reject, QUIC-before-DPI, LAN по API, health probes, fail-closed fallback) |
| DnsValidation | 3/3 (IP/HTTPS validation, DoH bootstrap selection, bootstrap emission) |
| DpiPresets | 4/4 |
| RoutesMapping | 5/5 |

## Приёмка на OnePlus 13s (Task 11)

Окружение: CPH2723, ColorOS (Android 15), сеть провайдера с DPI-блокировками
(Wi-Fi HUAWEI 5 ГГц + LTE), byedpi v0.17.3, исторический preset id COMPATIBLE (текущий RECOMMENDED).

| # | Проверка | Результат | Доказательство |
|---|---|---|---|
| 1 | Telegram → VPN | PASS | IP-бот в Telegram показывает адрес VLESS-сервера |
| 2 | YouTube → DPI | PASS | Видео воспроизводится; в mihomo.log `match Uid(10218) using DPI` |
| 3 | Браузер без назначения → DIRECT | PASS | ifconfig.me = IP провайдера |
| 4 | Три маршрута одновременно | PASS | Один VpnService, один TUN |
| 5 | QUIC | PASS* | UDP/443 быстро REJECT-ится правилом → приложение откатывается на TCP; TCP до GGC-кэшей проходит настроенной лестницей |
| 6 | Wi-Fi ↔ LTE | PASS | Автопереподключение монитором сети, маршруты сохранены |
| 7 | Перезагрузка устройства | PASS | DataStore цел; повторное подключение и YouTube работают |
| 8 | LAN при туннеле | PASS* | `192.168.0.0/16 throw` в маршрутах VPN: LAN не захватывается туннелем и не проксируется (LNP-инвариант) |
| 9 | Секреты в логах | PASS | 508 строк mihomo.log: 0 совпадений по uuid/vless:///pbk; ServiceLog без секретов |

\* см. «Найденные отклонения» — поведение QUIC осознанно изменено против исходного плана.

### Найденные и исправленные дефекты

1. **AppsScreen на узких экранах**: сегментные кнопки в одной строке с названием
   сжимали колонку имени до вертикального столбика букв (RU-локаль). Кнопки
   вынесены на отдельную полную строку.
2. **Согласие VPN не продолжало подключение**: после одобрения системного диалога
   требовался второй тап. Теперь старт перезапускается через
   `rememberLauncherForActivityResult` на RESULT_OK.
3. **Шторм рестартов сетевого монитора**: `onAvailable` от самой VPN-сети и при
   валидации вызывал бесконечные ACTION_RESTART. Монитор дедуплицирует сеть,
   игнорирует TRANSPORT_VPN и рестартует только из Active.
4. **Статический ciadpi**: у статического bionic-бинарника не работает
   `getaddrinfo` (нет libnetd_client) — сборка переведена на динамическую
   (`interpreter /system/bin/linker64`).
5. **IPv6 fail-closed**: IPv6 захватывается в TUN и отклоняется правилом
   `IP-CIDR6,::/0,REJECT`; выбранные приложения не обходят VPN.
6. **Заморозка ciadpi на пайпе**: stdout/stderr дочернего процесса никто не
   читал; после заполнения пайпа (~64KB предупреждений) процесс блокировался на
   записи. Вывод перенаправлен через `Redirect.INHERIT`.
7. **Пресет COMPATIBLE**: исторический id стратегии мигрирует на текущий
   RECOMMENDED; tuned ladder и `--timeout 3` golden-pinned unit-тестом.
8. **fake-IP TUN prefix drift**: Android Builder использовал `198.18.0.1/16`,
   хотя device-spike contract и историческая успешная сессия требуют
   `198.18.0.1/30`. Восстановлен `/30`; external-FD mode сам интерфейс не
   конфигурирует.
9. **Backup import поверх активной сессии**: DataStore уже показывал новые
   profiles/routes, а старый tunnel продолжал работать. После успешного импорта
   Active/Starting tunnel теперь останавливается; импорт по-прежнему принудительно
   оставляет auto-connect выключенным и не запускает новый endpoint сам.
10. **Stale route summaries**: Home, Settings и Quick Settings считали сохранённые
    маршруты, включая удалённые приложения/небезопасные shared UID. Отображение
    переведено на тот же `resolveEffectiveRoutes`, что использует service-start,
    с refresh после возврата приложения в foreground.

### Current automated/device limitations

- GitHub Actions проверяет JVM tests, lint, debug APK assembly, patched-engine Go tests и source/binary vulnerability scans; `connectedDebugAndroidTest` требует отдельный эмулятор/устройство и не входит в текущий CI job.
- OnePlus/AVD evidence выше историческое. После текущего corrective pass ещё нужен свежий device smoke на текущем head, особенно для TUN `/30`, backup import во время Active и effective-route summaries после изменения installed packages.
- Current policy captures IPv6 in the TUN and explicitly rejects it, preventing selected applications from bypassing the VPN over IPv6.
- DNS accepts only IP literals or HTTPS DoH URLs and is emitted through mihomo DNS hijack for routed applications.

### Итог

The historical device runs above are not a substitute for current automated verification commands.

### Подбор стратегии DPI (МТС Вологда, 2026-08-24)

Методика: нативный ciadpi v0.17.3 + перебор 63 кандидатов (курируемый список
ByeByeDPI `proxytest_strategies.list` + пресеты) с метриками: TLS-рукопожатие
к 5 реальным `rrX.googlevideo.com`, youtubei API, длинная закачка (143 МБ,
dl.google.com), матрица флакнесса 5 хостов × 6 повторов на телефоне.

Выводы:
- Одиночные приёмы (`-s N`, `-d N`, `--fake --ttl`, `-r`) не пробивают
  SNI-блок МТС вовсе; работают только многочастные лестницы/комбо.
- Все рабочие стратегии статистически равны (18–24/30 прохождений).
- Разброс зависит от конкретных GGC-нод: часть нод (напр.
  `rr4---sn-q4flrnsl`) недоступна даже через обход — это деградация сети,
  а не стратегии. Отсюда периодические «видео не грузится» с самоисправлением.
- Длинные потоки стратегии не рвут (143 МБ @ ~20 МБ/с у всех финалистов);
  ранний капибюз 16 КБ на proof.ovh оказался анти-ботом OVH, а не DPI.

Действие: текущий RECOMMENDED содержит проверенную ladder-стратегию и
`--timeout 3` (по исходнику действует только на фазу установления соединения) —
мёртвая нода отваливается за 3 с, YouTube перебирает следующую вместо минутного
зависания. Исторические альтернативы исследования использовали `-q`, `-r`,
`-o`, `--fake` и другие флаги; текущий Custom validator намеренно разрешает
только `-d`, `-s`, `-a` и `--timeout`, поэтому эти старые варианты не являются
поддерживаемыми примерами пользовательской конфигурации.

## Осталось до релиза

- Fresh device smoke на текущем head: VPN connect/routing после восстановления
  fake-IP `/30`; импорт backup во время Active должен остановить текущий tunnel
  без auto-connect; Home/Settings/Tile должны пересчитать effective routes после
  удаления/установки приложения.
