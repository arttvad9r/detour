# Public launch kit

Detour should be introduced as a concrete routing tool, not as another generic VPN client.

## Positioning

Primary message:

> One Android tunnel. A different route for every app: Direct, VPN or DPI.

Concrete example:

> Keep banking and local services direct, send selected apps through VPN, and use ByeDPI only where it is needed.

Avoid leading with a long protocol list. Protocol support is proof after the user already understands the product idea.

## GitHub discovery

Recommended repository description:

`Per-app Direct, VPN and ByeDPI routing for Android. VLESS Reality, WARP, AmneziaWG and more.`

Recommended topics:

`android`, `vpn`, `vless`, `wireguard`, `amneziawg`, `byedpi`, `dpi-bypass`, `split-tunneling`, `kotlin`, `jetpack-compose`, `open-source`

GitHub topics and the repository social-preview image are repository settings and should be kept aligned with the README positioning.

## Social preview

Use a clean 1280×640 image with:

- Detour name and app icon;
- the phrase `Direct · VPN · DPI`;
- one or two real app screens, not a dense feature collage;
- no real profile names, subscription URLs, server addresses, UUIDs or private keys;
- enough empty space that the preview remains readable in small cards.

Do not use generated fake UI screenshots as product evidence.

## First Russian announcement

**Detour — open-source маршрутизация приложений на Android**

Сделал Detour для сценария, который обычные VPN-клиенты решают неудобно: разным приложениям нужен разный маршрут.

В Detour для каждого приложения можно выбрать **Прямой / VPN / DPI**. Например, локальные сервисы оставить напрямую, выбранные приложения отправить через VPN, а ByeDPI включить только там, где он действительно нужен.

Сейчас поддерживаются VLESS Reality, HTTPS-подписки, WARP, AmneziaWG, DNS внутри туннеля и тестирование стратегий ByeDPI. Код открыт, аккаунт не нужен.

Проект пока pre-release, поэтому особенно полезны реальные тесты, баг-репорты и обратная связь по совместимости устройств.

Repository: https://github.com/arttvad9r/detour

## First English announcement

**Detour — open-source per-app routing for Android**

Detour is built around one idea: different apps should not have to use the same network route.

For every app you can choose **Direct / VPN / DPI**. Keep local services direct, route selected apps through VPN, and use ByeDPI only where it is actually needed.

Current support includes VLESS Reality, HTTPS subscriptions, WARP, AmneziaWG, tunnel DNS and ByeDPI strategy testing. The code is open source and no Detour account is required.

The project is still pre-release, so device testing, bug reports and compatibility feedback are especially useful.

Repository: https://github.com/arttvad9r/detour

## Where to announce

Prefer communities where the problem is already discussed: Android FOSS/privacy communities, VPN and censorship-circumvention communities, Russian Android forums, Habr/Telegram developer communities, and relevant GitHub discussions.

Post as a technical/product introduction, not as repeated link promotion. Show one concrete use case, disclose the pre-release state, link to source and Releases, and stay in the thread to answer implementation and security questions.
