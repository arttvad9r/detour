# Pins
- mihomo: v1.19.29 (patch: buildAndroidRules -> nil)
- gomobile: latest installed in GOPATH at build time

## Attribution decision

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
- ATTRIBUTION = **UID**; per-app фильтрация делается хостом через VpnService
  allow/disallow списки по uid (package->uid резолвим сами через PackageManager),
  а НЕ правилами движка: ConfigGenerator не должен полагаться на find-process/UID rules.

## Engine build pin
- AAR обязан собираться с `-tags with_gvisor` (`stack: gvisor` иначе падает:
  "gVisor is not included in this build"); `stack: system` в embedded-режиме не
  перехватывает TCP (нет [TCP] логов, чёрная дыра) — только ICMP.
- mihomo v1.19.x игнорирует `tun.inet4-address`: адрес TUN = fake-ip range gateway
  (/30 от 198.18.0.1); VpnService.Builder обязан добавлять 198.18.0.1/30.
- YAML: IPv6 CIDR в flow-последовательностях надо квотировать (`"::/1"`), go-yaml падает.
