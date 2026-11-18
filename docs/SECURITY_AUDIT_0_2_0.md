# Security review 0.2.0

Scope: исходники Android, dependency trust, native source pins, ограничение данных, внешние ссылки и локальные transport fixtures. Это code review и доступные тесты, не формальный пентест/CVE certification.

| Область | Результат |
|---|---|
| Dependency Verification | Strict включён, trust metadata не расширены в 0.2.0; исходная Guava причина/проверенные SHA — TEST_REPORT history |
| Native | Официальный pinned AAR; host archive SHA проверяется; private fixture keys игнорируются |
| Storage | AES-GCM/Keystore, backup false; failed vault не перезаписывается; commit перед StateFlow |
| Input | Subscription 4 MB, 8 subscriptions/500 profiles/8 MB config budget; bounded redirects/timeouts; QR 16 MB/downsampling |
| Secrets | Deep links требуют подтверждения; AGE/input не SavedState, очищаются; debug file logging удалён |
| Probes | Separate process, bounded FD pipe, максимум 5, lifecycle cancellation; authenticated temporary proxy |
| Listeners | Основной SOCKS удалён; metrics loopback без UID privacy остаётся известным риском |
| Updates | Public HTTPS GitHub, allowlisted release URLs/ABI/числовой SemVer, no token/HWID/auto-download; Android проверяет подпись при установке |
| OAuth | Явное согласие подписке для XDrive OAuth; реальных токенов/входов в стенде нет |
| TLS | Не выключалась проверка; fixture CA только в тестовом клиенте; wrong-name rejection PASS на host |
| VPN | BIND_VPN_SERVICE, non-exported probe/scanner, IPv4/IPv6 routes, real HTTPS до CONNECTED, bounded retries |

Остаточные риски: debug certificate, metrics/debug endpoints доступны локальным приложениям, произвольный доверенный JSON может намеренно направлять трафик direct или менять TLS-политику (UI предупреждает allowInsecure), custom geodata/несколько ingress требуют проверки. Пользовательский JSON не является безопасной политикой автоматически. Always-on/lockdown — системный механизм; без него нет обещания kill switch при всех сбоях/force-stop.

Физические leak tests, work-profile/per-app acceptance, IPv6 egress/MTU, длительный фон/soak, dependency CVE audit и production signing: NOT RUN. Инфраструктура Signet не изменялась. Никаких обещаний универсального обхода DPI.
