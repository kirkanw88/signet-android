# Xray / libXray

Закреплены официальные релизы libXray v26.11.9 и Xray-core v26.11.9, Core commit `676eaf66f8f0`. libXray использует Invoke API v3. AAR содержит arm64-v8a, armeabi-v7a, x86 и x86_64; выдаваемые ABI APK исключают остальные архитектуры. Архив и SHA-256 — `assets/native-source.json`.

Источники: [libXray v26.11.9](https://github.com/XTLS/libXray/tree/v26.11.9), [native TUN](https://github.com/XTLS/Xray-core/tree/v26.11.9/proxy/tun), [Android VpnService](https://developer.android.com/develop/connectivity/vpn).

Используются `xrayVersion`, `runXray`, `stopXray`, `getXrayState`, `testXray`, `getFreePorts`, `pingBatch`, `decodeSubscriptionText`, `convertShareLinksToXrayJson`. В 0.2.0 testXray выполняется в отдельном процессе и доступен при активном VPN. Все ошибки API проверяются по success; сырые native ошибки не попадают в экспорт.

Android остаётся владельцем исходного TUN descriptor. Core дублирует его и закрывает собственную копию. Сначала останавливается Core, затем закрывается исходный ParcelFileDescriptor; DNS resolver сбрасывается после остановки Core. Для socket protection зарегистрированы dialer/listener controller. Метрики читаются из loopback `/debug/vars`, счётчики не подменяются таймерами или статистикой всего UID.

Полный импортированный JSON хранится целиком. Вход заменяется TUN с tag/sniffing исходного TUN/SOCKS/HTTP ingress; первый DNS-inbound не выбирается ошибочно. Лишний SOCKS удалён. Исходный JSON с DNS, routing, balancers, observatory, transport и неизвестными полями хранится без изменения. Начиная с 0.2.3 runtime добавляет внутренний DNS-мост, описанный ниже. Метрики и запрет файловых логов задаются приложением. Один TUN не воспроизводит несколько независимых ingress. Для custom geodata нужны доступные файлы; отдельного менеджера .dat в 0.2.0 нет. См. REMNAWAVE_TEMPLATE_COMPATIBILITY.md.

HTTP-пробы дают HTTP response time в ms, не ICMP ping. Публичный endpoint — `https://www.gstatic.com/generate_204`; диагностическая проверка DNS — `example.com`. Успешный запуск Core и успешный TLS-запрос не означают доказанную совместимость всех возможных routing-профилей.

См. TEST_REPORT для разделения парсинга и настоящих протокольных подключений. Hysteria2 предоставляется закреплённым ядром; не следует объявлять его передачу данных проверенной только по успешному импорту URI.

## Системный DNS — 0.2.3

VpnService объявляет виртуальный DNS 172.19.0.2. AndroidRuntime добавляет правило TCP/UDP :53 к этому /32 из своего ingress → встроенный DNS outbound Xray. Остальные назначения/порты и порядок исходных правил сохраняются. Само наличие dns в JSON не связывало netd Android со встроенным DNS, поэтому прежняя версия отправляла системные DNS-пакеты через обычную маршрутизацию и могла не применять DoH/hosts провайдера. Для профиля без dns резервный DNS использует tcp://IP:53 через routing. Существующий DNS outbound сохраняется и переиспользуется. Новые служебные handlers исключаются из AUTO/observatory selectors без изменения первоначального состава узлов; исходный документ в vault не переписывается.

Bootstrap libXray получает IP DNS физической сети; защищённые исходящие sockets дополнительно привязываются к underlying Network. Приложение не привязывает собственную HTTPS-проверку к физической сети. TLS, TUN и Dependency Verification остаются обязательными. Локальный тест scripts/check-dns-bridge.py проверяет UDP/TCP DNS через тот же adapter на настоящем Xray; TUN/netd физического телефона этим тестом не покрыты.

Источники: [Xray TUN](https://github.com/XTLS/Xray-core/blob/v26.11.9/proxy/tun/README.md), [DNS outbound](https://xtls.github.io/en/config/outbounds/dns.html), [DNS](https://xtls.github.io/en/config/dns.html), [prefix selectors](https://github.com/XTLS/Xray-core/blob/v26.11.9/app/proxyman/outbound/outbound.go#L155), [libXray Android DNS](https://github.com/XTLS/libXray/blob/v26.11.9/dns/dns_android.go).
