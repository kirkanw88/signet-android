# Xray / libXray

Закреплены официальные релизы libXray v26.11.9 и Xray-core v26.11.9, Core commit `676eaf66f8f0`. libXray использует Invoke API v3. AAR содержит arm64-v8a, armeabi-v7a, x86 и x86_64; выдаваемые ABI APK исключают остальные архитектуры. Архив и SHA-256 — `assets/native-source.json`.

Источники: [libXray v26.11.9](https://github.com/XTLS/libXray/tree/v26.11.9), [native TUN](https://github.com/XTLS/Xray-core/tree/v26.11.9/proxy/tun), [Android VpnService](https://developer.android.com/develop/connectivity/vpn).

Используются `xrayVersion`, `runXray`, `stopXray`, `getXrayState`, `testXray`, `getFreePorts`, `pingBatch`, `decodeSubscriptionText`, `convertShareLinksToXrayJson`. `testXray` вызывается только при остановленном VPN. Все ошибки API проверяются по success; сырые native ошибки не попадают в экспорт.

Android остаётся владельцем исходного TUN descriptor. Core дублирует его и закрывает собственную копию. Сначала останавливается Core, затем закрывается исходный ParcelFileDescriptor; DNS resolver сбрасывается после остановки Core. Для socket protection зарегистрированы dialer/listener controller. Метрики читаются из loopback `/debug/vars`, счётчики не подменяются таймерами или статистикой всего UID.

Полный импортированный JSON хранится целиком. В рабочей копии заменяются inbounds на native TUN + локальный SOCKS для проверки; сохраняется tag первого исходного inbound, чтобы стандартные inboundTag-правила продолжали совпадать. Подписочные DNS, routing, balancers, observatory, transport settings и неизвестные поля сохраняются. Метрики и запрет файловых логов задаются приложением. Конфигурации с несколькими семантически разными входами могут требовать адаптации: один системный TUN не воспроизводит несколько независимых исходных listeners. Внешние файловые пути к custom geodata должны быть доступны приложению; отдельного менеджера пользовательских .dat в версии 0.1.0 нет.

HTTP-пробы дают HTTP response time в ms, не ICMP ping. Публичный endpoint — `https://www.gstatic.com/generate_204`; диагностическая проверка DNS — `example.com`. Успешный запуск Core и успешный TLS-запрос не означают доказанную совместимость всех возможных routing-профилей.

См. TEST_REPORT для разделения парсинга и настоящих протокольных подключений. Hysteria2 предоставляется закреплённым ядром; не следует объявлять его передачу данных проверенной только по успешному импорту URI.
