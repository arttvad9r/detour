<p align="center">
  <a href="README.md">English</a> · <strong>Русский</strong>
</p>

<div align="center">

# Detour

### Маршрутизация приложений на Android.

**Прямой · VPN · DPI**

Detour — open-source клиент для Android, в котором **каждому приложению можно назначить свой маршрут**. Одни приложения работают напрямую, другие идут через VPN, а ByeDPI используется только там, где он нужен — всё внутри одного Android VPN-сервиса.

[**Скачать последнюю APK**](https://github.com/arttvad9r/detour/releases/latest) · [Быстрый старт](docs/getting-started.md) · [Все возможности](docs/features.md)

[![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](https://github.com/arttvad9r/detour/releases/latest)
[![Android CI](https://github.com/arttvad9r/detour/actions/workflows/android.yml/badge.svg)](https://github.com/arttvad9r/detour/actions/workflows/android.yml)
[![Latest release](https://img.shields.io/github/v/release/arttvad9r/detour)](https://github.com/arttvad9r/detour/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

<table align="center">
  <tr>
    <td align="center"><img src="docs/assets/screenshots/home-connected.jpg" width="230" alt="Главный экран Detour"><br><strong>Всё подключение на одном экране</strong></td>
    <td align="center"><img src="docs/assets/screenshots/app-routes.jpg" width="230" alt="Маршруты приложений Detour"><br><strong>Прямой / VPN / DPI для каждого приложения</strong></td>
    <td align="center"><img src="docs/assets/screenshots/vpn-profiles.jpg" width="230" alt="VPN-профили Detour"><br><strong>Несколько VPN-профилей</strong></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/assets/screenshots/vpn-add-profile.jpg" width="230" alt="Добавление VPN-профиля Detour"><br><strong>Удобный импорт профилей</strong></td>
    <td align="center"><img src="docs/assets/screenshots/dpi-proxy-test.jpg" width="230" alt="Тест стратегий ByeDPI в Detour"><br><strong>Подбор стратегии ByeDPI</strong></td>
    <td align="center"><img src="docs/assets/screenshots/appearance.jpg" width="230" alt="Оформление Detour"><br><strong>Светлые, тёмные и community-темы</strong></td>
  </tr>
</table>

<p align="center"><sub>Реальные скриншоты Detour с Android-устройства. Без сгенерированных макетов приложения.</sub></p>

## Один туннель. Три маршрута.

Обычный VPN-клиент чаще всего принимает одно решение для всего телефона. В Detour маршрут задаётся **для каждого приложения отдельно**:

- **Прямой** — обычное подключение к интернету без туннеля.
- **VPN** — выбранные приложения идут через VLESS Reality, HTTPS-подписку, WARP или AmneziaWG.
- **DPI** — выбранные приложения идут через нативный обход ByeDPI.

То, чему VPN не нужен, можно оставить вне туннеля. Для разных приложений не приходится постоянно переключаться между отдельными клиентами и режимами.

## Что умеет Detour

- **Маршруты для приложений** — поиск и отдельный выбор Прямой / VPN / DPI.
- **VPN-профили** — VLESS Reality, HTTPS-подписки, WARP и AmneziaWG, включая поддерживаемый импорт Amnezia `vpn://`.
- **Работа с подписками** — выбор сервера, поиск, сортировка, проверка задержки и состояние узлов во время работы.
- **ByeDPI** — рекомендуемая и своя стратегия плюс отдельный «Тест стратегий» для проверки выбранных хостов.
- **DNS внутри туннеля** — Cloudflare, Google, AdGuard, свой IP или HTTPS DNS-over-HTTPS.
- **Интеграция с Android** — плитка быстрых настроек, подключение при запуске, управление активным подключением, экспорт/импорт и несколько тем оформления.

Точные форматы и поведение перечислены в [полном описании возможностей](docs/features.md).

## Установка

Нужен **Android 10 / API 29 или новее**. В текущем публичном релизе на GitHub публикуется APK для `arm64-v8a`.

1. Скачайте APK из [последнего релиза GitHub](https://github.com/arttvad9r/detour/releases/latest).
2. Добавьте или импортируйте VPN-профиль и назначьте маршруты нужным приложениям.
3. Нажмите **«Подключить»** и при первом запуске разрешите Android создать VPN-подключение.

Настройка профилей, DNS, DPI и типовые проблемы описаны в [руководстве по быстрому старту](docs/getting-started.md).

> [!NOTE]
> Detour пока находится на стадии pre-release. Проверяйте импортированные конфигурации перед использованием для чувствительного трафика.

## Открытый код и приватность

Для Detour не нужен аккаунт проекта. Приложение намеренно не отправляет аналитику проекта и автоматические crash-отчёты. Чувствительные данные профилей хранятся локально с шифрованием на основе Android Keystore.

Код, написанный для Detour, распространяется по [лицензии MIT](LICENSE). Встроенные компоненты сохраняют собственные лицензии и требования к распространению — подробности в [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Документация

[Быстрый старт](docs/getting-started.md) · [Возможности](docs/features.md) · [Архитектура](docs/architecture.md) · [Тестирование](docs/testing.md) · [Приватность](PRIVACY.md) · [Безопасность](SECURITY.md) · [Участие в разработке](CONTRIBUTING.md)

<details>
<summary><strong>Разработчикам и мейнтейнерам</strong></summary>

Detour написан на Kotlin и Jetpack Compose. Android `VpnService` управляет туннелем, Mihomo используется как основной data plane, а нативный ByeDPI интегрирован для DPI-маршрута. Сборка, тесты, версии зависимостей и выпуск релизов вынесены из главной страницы в отдельную документацию:

- [Архитектура](docs/architecture.md)
- [Тестирование и локальная проверка](docs/testing.md)
- [Закреплённые версии нативных зависимостей](docs/pins.md)
- [Процесс релиза](docs/releasing.md)
- [Чек-лист релиза](docs/release-checklist.md)

</details>
