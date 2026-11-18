# Транспорты 0.2.0

Закреплён официальный libXray v26.11.9 / Xray commit 676eaf66f8f05d4d558ee7a74b4736623d3feeb9. Нижняя подтверждённая версия для XDrive/MASQUE — 26.11.9; историческая первая версия не исследовалась. Native AAR не заменялся сторонней сборкой.

| Формат | Импорт | Host e2e | Android 0.2.0 | Ограничения |
|---|---|---|---|---|
| VLESS TCP | JSON/share | PASS | Instrumentation job | Plain TCP только local fixture |
| REALITY/Vision | JSON/share | PASS | Instrumentation job | Нужны корректные key/SNI/flow |
| XHTTP REALITY | JSON/share | PASS | Instrumentation job | Packet-up проверен, не все режимы |
| XDrive template | Full JSON | PASS | Instrumentation job | VLESS Encryption + Mux; не подмена XHTTP |
| XDrive google-drive | Full JSON + consent | NOT RUN | NOT RUN | Реальный OAuth не авторизован/не выполнялся |
| XDrive s3/local/provider template | Full JSON | NOT RUN | NOT RUN | Backend credentials/shared storage требуются извне |
| MASQUE HTTP/2 CONNECT-IP | Full JSON | PASS | Instrumentation job | TLS, no Mux, корректная auth |
| MASQUE HTTP/3 CONNECT-IP | Full JSON | PASS | Instrumentation job | UDP reachable, не CONNECT-UDP |
| Hysteria2 | JSON/share | NOT RUN | Conversion test | Парсинг не равен передаче данных |
| Remnawave AUTO balancer / split GeoIP/geosite | Rendered JSON | PASS | Instrumentation job | Sniffing/domain context, geodata, ingress scope |

Host PASS включает TCP/HTTPS/UDP DNS/отказ TLS wrong-name. Android окончательные статусы — TEST_REPORT и CI artifacts. Это не утверждение о совместимости всех комбинаций настроек, сетей или production-панели. Автоматических downgrade/fallback/перезаписи transports нет. Типовые template ограничения — REMNAWAVE_TEMPLATE_COMPATIBILITY.md.
