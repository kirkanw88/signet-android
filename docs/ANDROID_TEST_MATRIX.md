# Android 0.2.0 test matrix

Статусы не переносятся с предыдущей версии. Успешная сборка APK не означает runtime PASS.

| Сценарий | Статус / доказательство |
|---|---|
| Core import/refresh/AUTO/metadata/routing/versions/geometry | 42 JVM tests PASS |
| Android instrumented APK | Compile PASS; runtime результат в TEST_REPORT |
| Linux KVM API36 x86_64 | Новый CI runtime job; результат после выполнения |
| VLESS/REALITY/XHTTP through TUN | Instrumentation: consent, outbound probe, CONNECTED после HTTPS, HTTPS и ненулевые счётчики, probe при VPN |
| XDrive/MASQUE h2/h3 through TUN | Instrumentation: native validate, реальный TCP/HTTPS/UDP DNS, probe без изменения исходной политики |
| Remnawave AUTO/split through TUN | Instrumentation: исходные balancer/rules, разрешённый Host и sniffed block, DNS |
| QR image | Instrumentation: сгенерированный PNG → исходная HTTPS ссылка; не аппаратная камера |
| UI | Instrumentation: navigation/recreate/import cancel/update control |
| Responsive release | capture-matrix.py: 360×640, 393×851, 412×915, 600×960, 800×1280, 640×360, font 1.3; реальный результат после CI |
| Physical API26–35 ARM64/ARMv7 | NOT RUN, устройств нет |
| Камера/фонарик/revoke/двойное сканирование на телефоне | NOT RUN; permission/lifecycle/error handling реализованы |
| Wi-Fi/mobile handover, Always-on/lockdown, reboot | NOT RUN |
| Per-app physical/work profile, DNS leak/IPv6 egress | NOT RUN |
| 1h+ soak, PSS trend, батарея/температура/FPS | NOT RUN; один meminfo не доказывает отсутствие утечек |

Fixture endpoint — только localhost/эмуляторный 10.0.2.2, временная тестовая CA, тестовые UUID. Реальные subscriptions, Google OAuth и платежи не используются. Политика TLS остаётся строгой. Host результаты отдельно: tests/evidence/0.2.0/transport-host.json.
