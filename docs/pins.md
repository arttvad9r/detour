# Pins
- mihomo: v1.19.29 (patch: buildAndroidRules -> nil; triplet host-resolver bridge in tunnel.go/process.go)
- byedpi: v0.17.3 (ciadpi cross-compiled via engine/byedpi/build.sh; -static, android21, arm64-v8a + x86_64)

## Attribution decision

### Round 1: engine-native attribution мертва в embedded-режиме

Спайк (Task 3, эмулятор API 35, gvisor stack, embedded engine AAR):
- UID lookup in mihomo TUN: **не работает**
  (доказательство: при правиле `UID,2000,REJECT` TCP от shell-uid проходит:
  `warning [UID] could not get uid from 1.1.1.1`,
  `info [TCP] 198.18.0.1:41520 --> 1.1.1.1:80 doesn't match any rule using DIRECT`;
  причина: metadata.Uid берётся из netlink INET_DIAG / procfs — запрещено обычным
  приложениям на Android 14+; sing-tun packages.xml недоступен из приложения)
- PROCESS-NAME package matching: **не работает** (процесс и пакет)
  (доказательство: правила `PROCESS-NAME,ping|nc|com.google.android.gms.persistent,REJECT`
  не срабатывают, соединения идут DIRECT;
  `debug [Process] find process error for 173.194.220.188: process not found` на каждом соединении)
- Контроль: `IP-CIDR,173.194.220.188/32,REJECT` срабатывает
  (`match IPCIDR(173.194.220.188/32) using REJECT`) → TUN+rules pipeline исправен,
  сломана именно атрибуция.

### Round 2: host-side resolver bridge — оба механизма работают для app-uid

Мост: `process.TripletHostFinder` (патч tunnel.go FindProcess-closure) ← `Engine.setProcessResolver`
← Android `ConnectivityManager.getConnectionOwnerUid` (техника PCAPdroid, VPN-owner privilege,
API 29+), резолвится ДО Engine.start. Пробы (эмулятор API 35, gvisor, find-process-mode strict):

| Probe | Rule | Traffic | Result |
|---|---|---|---|
| A' | `UID,10143,REJECT` | GMS :5228 (uid 10143) | **PASSED**: `match Uid(10143) using REJECT` |
| B/B2 | `PROCESS-NAME,ping\|nc,REJECT` | shell ping/nc (uid 2000) | **NOT blocked** — `getConnectionOwnerUid` вернул 0 для shell-сокета (`TripUid resolve ... => raw=0`) → без атрибуции rule не матчится |
| C | `PROCESS-NAME,com.google.uid.shared,REJECT` | GMS :5228 | **PASSED**: `match ProcessName(com.google.uid.shared) using REJECT` |

Наблюдения:
- JNI-коллбек срабатывает на каждом коннекте (`resolve tcp ... => raw=10143`);
  атрибуция видна и в логе движка: `[TCP] ...(com.google.uid.shared, uid=10143)`.
- Shell/system uids (2000) `getConnectionOwnerUid` не резолвит (raw=0) — для
  продукта неважно (фильтруем app-uids 10000+); ICMP вообще не доходит до rules.
- PROCESS-NAME зависит от нашего pkgOf(): `getNameForUid` для shared-uid даёт
  имя shared-uid (`com.google.uid.shared`), не пакет — ещё один аргумент против.

- **ATTRIBUTION = UID** (подтверждено раундом 2).
- ConfigGenerator: если эмитить attribution-правила движка — **только UID-правила**
  (детерминированно: package->uid резолвим сами через PackageManager; PROCESS-NAME
  зависит от имён shared-uid). Основной механизм per-app фильтрации остаётся
  хостовым: VpnService allow/disallow списки по uid (kernel-enforced, трафик не
  входит в TUN, нет per-connection JNI/netd lookup). Мост — валидированный
  fallback для движковых правил.

## Engine build pin
- AAR обязан собираться с `-tags with_gvisor` (`stack: gvisor` иначе падает:
  "gVisor is not included in this build"); `stack: system` в embedded-режиме не
  перехватывает TCP (нет [TCP] логов, чёрная дыра) — только ICMP.
- mihomo v1.19.x игнорирует `tun.inet4-address`: адрес TUN = fake-ip range gateway
  (/30 от 198.18.0.1); VpnService.Builder обязан добавлять 198.18.0.1/30.
- YAML: IPv6 CIDR в flow-последовательностях надо квотировать (`"::/1"`), go-yaml падает.
