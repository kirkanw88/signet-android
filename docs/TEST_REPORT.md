# Signet VPN 0.2.9 — проверка 19.11.2026

Исправления только универсальной редакции external: JSON/Signet_Auto, описания Remnawave и светлая тема. Signet с кабинетом не пересобирается и остаётся на 0.2.6. [Описание и обновление подписки](RELEASE_0_2_9.md).

Причины в коде: при отделении external был отключён явный JSON endpoint; native Signet UA не входит в upstream extended-clients list Remnawave, от которого зависит `meta.serverDescription`; UI выбирал между provider description и строкой формата, а для AUTO подставлял своё описание. Вектор логотипа содержал белые пути без tint для светлой темы, GL-область продолжала рисовать тёмный космос независимо от темы.

Теперь получение JSON/расширенных метаданных ограничено исходным origin с сохранением HWID и реального Signet UA в compatibility agent. Исходный отказ доступа не вызывает дополнительных запросов. URI-only и не-Xray JSON не подменяют рабочую выдачу. DNS/routing/balancers/outbounds доставленного AUTO сохраняются. Протокол/формат и описание показаны отдельными строками, Signet_Auto остаётся узнаваемым.

| Проверка | Результат |
|---|---|
| Core targeted JVM | ProfilesTest + DisplayAndSettingsTest + SubscriptionRequestTest: 25/25, failures/errors 0 |
| External app JVM | 12/12, failures/errors 0; 6 новых HTTP-negotiation tests + прежние TLS/TCP/log tests |
| JSON negotiation fixture | Native URI → same-origin `/json` с extended metadata, AUTO/описание/opaque routing сохранены |
| Compatibility negatives | URI fallback при 403/404/500, IOException, sing-box JSON; original 403 не повторяется, JSON redirect на другой origin не выполняется |
| Existing JSON | JSON/Base64 JSON с описаниями не вызывает лишний запрос; JSON без extended metadata может получить описание |
| Native shaders | Точный GLSL ES compile/link PASS в WebGL1; не Android GPU/FPS тест |
| Android Lint external | 0 errors / 48 warnings |
| Debug / Release external | PASS, strict verification, финальная упаковочная сборка 2m 54s |
| APK | Три ABI, external 0.2.9/code 11, подпись v2 и zip/ELF 16 KB PASS |
| Physical Android / production customer subscription / screenshot / FPS | NOT RUN; проверены типовые обезличенные fixtures, публичный upstream contract и код шейдеров |
| Long CI / emulator / cold cache / production changes | Не запускались; production не изменён |

HTTP responses в новых negotiation tests моделируются OkHttp interceptor; они не заявляются проверкой реального Remnawave сервера. Прежние ConnectionHealth tests выполняют настоящий локальный TLS. Ссылки, ключи, UUID и содержимое JSON не попадают в журнал приложения. Новые зависимости и bitmap/blur buffers не добавлены. В тёмной теме `uLightTheme=0` сохраняет исходную формулу света и космоса.

[Checks](../tests/evidence/0.2.9/checks.json), [APK](../tests/evidence/0.2.9/apk-verification.json), [GLSL](../tests/evidence/0.2.9/glsl-check.json). После установки нужно нажать ↻ для подписки: старый URI-кеш не содержит JSON-only hosts и описаний, которые провайдер должен доставить заново. AUTO не создаётся из отсутствующих в ответе данных.

[Выпуск v0.2.9](https://github.com/kirkanw88/signet-android/releases/tag/v0.2.9) опубликован из `1247e9e`: три external APK, исходники и SHA256SUMS. Asset digests совпали с локальными SHA-256. Анонимный universal feed выбрал 0.2.9; общий latest остался 0.2.6. В Release DEX подтверждены JSON-negotiation и uniform светлой темы. [Подтверждение](../tests/evidence/0.2.9/public-release.json). ARM64: 50 360 487 bytes, SHA-256 `153b6e908bb752666d7c2c83d15cfff1e4d4f9c377f240a071aeb4e6dc18d1ed`.

---

# Signet VPN 0.2.8 — проверка 19.11.2026

Только универсальная редакция external; Signet с кабинетом остаётся 0.2.6 без новой сборки. [Описание изменений](RELEASE_0_2_8.md).

Добавлены светлая/тёмная/системная темы и сохранение выбора, установленная версия в шапке главного экрана; удалена надпись «Сторонние подписки». Палитры применены к доступным экранам, кнопкам, формам и окну обновления; камера и фото космоса сохраняют собственные тёмные области. Нет новых bitmap/blur buffers.

Обновления: успешная проверка свежа 15 минут вместо суток, неуспешная — 2 минуты; lifecycle STARTED повторно оценивает срок, по кнопке ожидание автоматического интервала обходится. «Позже» скрывает автоматический диалог, но не баннер доступного обновления. Восстановление кеша сохраняет прочее состояние UI. Пакет, подпись, SHA-256, TLS и PackageInstaller-проверки не ослаблены.

| Проверка | Результат |
|---|---|
| Targeted core JVM | AppReleaseTest + UpdatePollingTest: 10/10, failures/errors 0 |
| External Android JVM | 6/6, failures/errors 0 |
| Theme text contrast | 8 основных пар текста/фона, минимум 6.46:1; проверка палитр, не UI acceptance |
| Android Lint external | 0 errors / 48 warnings |
| Debug / Release external | PASS, strict verification, финальная сборка 3m 1s |
| APK | Три ABI, пакет external, версия 0.2.8/code 10, v2 signature и zip/ELF 16 KB PASS |
| Theme switching / persistence / new release banner on physical Android | NOT RUN; подключённых устройств нет |
| Long CI / emulator / cold cache / production | Не запускались; production не изменялся |

Lint при первом запуске потребовал `LocalActivity` для управления системными значками; исправлено без исключений и подавления правила. Новых зависимостей нет. Проверки ограничены затронутыми тестами и необходимой сборкой. [APK](../tests/evidence/0.2.8/apk-verification.json), [checks](../tests/evidence/0.2.8/checks.json), [контраст палитр](../tests/evidence/0.2.8/theme-contrast.json).

Старая universal 0.2.6 не может получить новую ленту без ручной установки APK: общий latest сохранён на 0.2.6 ради прежнего Signet. Universal 0.2.7 читает список Releases, но её старая автоматическая политика до установки 0.2.8 остаётся суточной; можно нажать «Проверить обновление». Установленная 0.2.8 не должна предлагать саму себя как обновление. Полный цикл физической установки и появления будущего обновления не выполнялся.

[Выпуск v0.2.8](https://github.com/kirkanw88/signet-android/releases/tag/v0.2.8) опубликован из `76d6213`: три external APK, исходники и SHA256SUMS. GitHub asset digests совпали с локальными SHA-256. Анонимная universal-лента выбрала 0.2.8; общий latest остался 0.2.6. В Release DEX подтверждены новые строки темы и ключ политики обновлений, удалённая надпись отсутствует. [Подтверждение](../tests/evidence/0.2.8/public-release.json). ARM64: 50 360 487 bytes, SHA-256 `594ef3ac6a1e995e5153b28c899098ac242eec9f3d617500d801b2efa63de762`.

---

# Signet VPN Universal 0.2.7 — проверка 19.11.2026

По запросу пользователя развивается только `external`. Редакция с кабинетом остаётся на опубликованной 0.2.6. [Изменения и установка](RELEASE_0_2_7.md).

Найдены различия между Ping и рабочим VPN: в процессе Ping отсутствовали `LibXray.setDNS` и привязка native sockets к физической сети, TCP проверял только первый DNS-адрес, ошибки IPC превращались в общий `FAILED` и показывались как отсутствие ответа серверов. Исправлена инициализация DNS/сети, TCP fallback и классификация результата. Это подтверждённые дефекты кода; без журнала телефона нельзя доказать, какой из них вызвал конкретный пользовательский случай. Изменённые UI/IPC пути для универсальной версии защищены flavor flag; прежнее тело Signet ProbeService сохранено без изменений в отдельном source set.

| Проверка | Результат |
|---|---|
| Targeted JVM | AppReleaseTest 8/8; external app 6/6, failures/errors 0 |
| TCP regression | Настоящий localhost: отказ первого IP → успешный второй IP; сокеты закрыты после успеха/ошибки |
| Android Lint external | 0 errors / 48 warnings |
| Debug + Release external | PASS, strict dependency verification, 3m 3s |
| Release APK | Три ABI, 0.2.7/code 9, пакет `com.signet.client.external`, подпись v2, zip/ELF 16 KB PASS |
| Signet freeze | Отдельная Signet-сборка не запускалась и не публиковалась; прежнее тело ProbeService совпало с 0.2.6 |
| Physical Android / VPN-on Ping / Wi-Fi–cellular transitions | NOT RUN; воспроизведение конкретного сбоя телефона не заявляется |
| Long CI / emulator / cold cache / production | Не запускались; production не изменён |

Новые зависимости не добавлены, verification-metadata не изменён. Адреса серверов/ключи не добавлены в логи; пишутся только коды результата Ping. Нет фиктивных latency, переключения серверов или отключения TLS-проверок. Полный профиль и исходный AUTO JSON сохраняются. [APK](../tests/evidence/0.2.7/apk-verification.json), [checks](../tests/evidence/0.2.7/checks.json).

Универсальные обновления 0.2.7+ выбираются из ограниченного публичного списка Releases. Выпуск публикуется без смены GitHub latest, чтобы старые APK Signet не предлагали чужой пакет. Первое обновление универсальной 0.2.6 → 0.2.7 — ручная установка APK поверх существующего приложения.

[Публичный выпуск v0.2.7](https://github.com/kirkanw88/signet-android/releases/tag/v0.2.7) опубликован из `356a321`: только три external APK, исходники и SHA256SUMS. Размеры и GitHub asset digests совпали с локальными SHA-256. Анонимный список Releases содержит совместимый universal 0.2.7, GitHub latest остался v0.2.6. [Подтверждение](../tests/evidence/0.2.7/public-release.json). ARM64: 50 360 487 bytes, SHA-256 `3e05e527f9f979f97c0c40bcff219259d385b881b578f286da82b11dbce4a06a`.

---

# Signet Android 0.2.6 — проверка 19.11.2026

Две редакции Signet VPN: исходная с кабинетом (`com.signet.client`) и сторонняя без кабинета и техподдержки (`com.signet.client.external`). Название, логотип, планета и VPN-функции сохранены. Изменения: [RELEASE_0_2_6](RELEASE_0_2_6.md). Backend/production/main не изменялись.

| Проверка | Результат |
|---|---|
| JVM core | 68 tests, 0 failures/errors; включая разделение APK и кеша обновлений по редакциям |
| Android JVM | По 4 tests в Signet и external, 0 failures/errors |
| Android Lint | Обе редакции: 0 errors, 48 warnings |
| Debug / Release | Обе редакции PASS, strict dependency verification; финальная сборка 5m 59s |
| APK | 6 ABI APK: package/version 0.2.6/code 8, одинаковое имя, v2 signature, zip/ELF 16 KB PASS |
| Cabinet removal | В external Release DEX отсутствуют `https://signet-cab.com`, `/api/mobile/v1`, `/support/messages`; в Signet сохранены |
| Manifest separation | OAuth callback только Signet; external import `signet-external`, отдельный пакет и startup authority |
| Signing identity | Тот же сертификат, что 0.2.5: `8158ed1e88c0d5e485abed87d63209f295021a482fc6c51d108e44fdce0448b1` |
| Physical Android / installation side by side / self-update | NOT RUN; разделение подтверждено пакетами и манифестами, реальная установка не заявляется |
| Long CI / emulator / cold Gradle cache | NOT REPEATED по просьбе ограничить проверки |

Первый запуск Lint обнаружил отсутствующий явный `android:exported` в новом Signet flavor manifest. Исправлено на `true`, как у исходной MainActivity; финальный Lint прошёл без исключений и отключения правил. Новых зависимостей и изменений verification-metadata нет. [APK evidence](../tests/evidence/0.2.6/apk-verification.json), [checks](../tests/evidence/0.2.6/checks.json).

Обычный Signet сохраняет applicationId, подпись и хранилище при обновлении 0.2.5. External устанавливается отдельным приложением; подписку нужно импортировать туда. Обе редакции используют собственный updater channel в общем публичном Releases. Старые APK 0.2.5 неизменны.

[Выпуск v0.2.6](https://github.com/kirkanw88/signet-android/releases/tag/v0.2.6) опубликован из `bb0029a`: 6 ABI APK, исходники и SHA256SUMS. Все asset digests совпали с локальными SHA-256, анонимный update feed вернул v0.2.6. Обычные Signet APK идут первыми: старый updater 0.2.5 выбирает свою редакцию. [Подтверждение публикации](../tests/evidence/0.2.6/public-release.json).

---

# Signet Android 0.2.5 — проверка 18.11.2026

Изменения: [RELEASE_0_2_5](RELEASE_0_2_5.md). Android ветка `feature/android-0.2.0`; main не объединялся. В рамках уже разрешённого развёртывания customer API исправлены app/main.py и app/mobile_api.py на действующем кабинете: резервный image/source сохранён, новый image проверен без сети, пересоздан только кабинет. БД не мигрировалась, bot/VPN nodes не менялись.

Причина `invalid_origin` воспроизведена в Chrome для всех трёх способов: no-referrer на HTML consent превращал browser form Origin в null, который корректно отвергал сервер. GET consent теперь same-origin, JSON API/redirect сохраняют no-referrer. Origin:null/foreign по-прежнему запрещены, правильный Origin без CSRF также отклоняется. Дополнительный CSP form-action self блокировал custom-scheme redirect: теперь разрешён только self и протокол com.signet.client:, а сам backend callback остаётся жёстко фиксированным. State/PKCE/code store не менялись.

[Публичный v0.2.5](https://github.com/kirkanw88/signet-android/releases/tag/v0.2.5) опубликован из `da8fd46`. Три ABI APK, source и SHA256SUMS загружены, asset digests совпали с локальными SHA-256. Точный endpoint updater без Authorization вернул v0.2.5. [Публикация](../tests/evidence/0.2.5/public-release.json). ARM64 50 376 943 bytes, SHA-256 `291b047caa629c6d5a0ed873d599d80d50a85e3efc627155b14eb28e0068a85f`. Сертификат проверен и совпадает с 0.2.1–0.2.4: `8158ed1e88c0d5e485abed87d63209f295021a482fc6c51d108e44fdce0448b1`.

| Проверка | Итог 0.2.5 |
|---|---|
| Server policy + payment-return regression | 10/10 PASS локально (0.142 s) и 10/10 в isolated image / network none |
| Real Chrome old/new | Для Telegram/Google/Yandex old: null/403; fixed: canonical Origin/303/correct callback+state, CSP violations=0 |
| Live provider starts | Telegram widget и Google/Yandex HTTPS provider/callback configuration PASS; credentials/MFA не использовались |
| Production hotfix | ACTIVE 2026-11-18T20:27:58.229614+00:00; кабинет running/restarts=0; API и login доступны |
| Debug / Release / Lint | PASS, STRICT verification; 2m 1s, 0 errors / 48 warnings |
| GLSL compile/link | PASS WebGL1; screen-space border uniform, не проверка Android FPS |
| APK identity/signature/alignment | PASS 0.2.5/code 7, три ABI, v2 signature, zip/ELF 16 KB |
| Physical Android / real provider login / complete self-update | NOT RUN |
| Long CI / emulator / cold cache | NOT REPEATED по просьбе ограничить проверки |

Browser provider replies и code store локально заменены stub, но настоящие Signet callback routes, session cookies, signed pending и form POST выполняются браузером. Desktop не имеет Android protocol handler; проверка callback URL не заявляется выполненным физическим входом. На production проверены только публичные starts без customer cookies, паролей, token minting или чтения клиентских данных. Существующие 29 JVM/27 backend результаты 0.2.4 ниже не пересчитываются как новые тесты 0.2.5.

Границы: центральная линия бело-голубая alpha 1.0, 4 offset pass обводки вместо зависимости от glLineWidth >1, заливка 17% вместо 7% при connecting/reconnecting/connected. Нет blur framebuffer и новых bitmap; цвета хранятся в переиспользуемых массивах. Реальный screenshot/FPS телефона не измерялся.

[Проверки](../tests/evidence/0.2.5/checks.json), [Chrome](../tests/evidence/0.2.5/browser-auth.json), [публичные providers](../tests/evidence/0.2.5/provider-starts.json), [APK](../tests/evidence/0.2.5/apk-verification.json), [шейдеры](../tests/evidence/0.2.5/glsl-check.json). Старую страницу invalid_origin нужно закрыть и начать вход заново из APK. Серверный hotfix действует также для 0.2.4.

Browser reference: [MDN Origin](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Origin), [Referrer-Policy](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Referrer-Policy). Исходники/backend deploy note сохранены локально в signet_cabinet/docs/MOBILE_AUTH_HOTFIX_0_2_5.md; закрытый код кабинета не публикуется в Android GitHub.

---

# Signet Android 0.2.4 — проверка 18.11.2026

Изменения: [RELEASE_0_2_4](RELEASE_0_2_4.md), [mobile API](MOBILE_API_CLIENT.md). Android-код остаётся в `feature/android-0.2.0`. По последующему прямому запросу пользователя также подготовлен customer API, затем **развёрнут по отдельному явному разрешению 18.11.2026 19:55:59 UTC**: две новые mobile таблицы, API flag и перезапуск кабинета. Бот/VPN nodes не менялись, main не объединялся. Backend bundle предоставляется отдельно локально, без публикации существующего закрытого кода кабинета в Android GitHub.

[Публичный выпуск v0.2.4](https://github.com/kirkanw88/signet-android/releases/tag/v0.2.4) опубликован из `e967dce`: три ABI APK, исходники и SHA256SUMS. Asset digests совпали с локальными SHA-256, анонимный `/releases/latest` вернул v0.2.4. [Подтверждение](../tests/evidence/0.2.4/public-release.json). ARM64 50 376 943 bytes, SHA-256 `3a3fc969a7115e1c75a90c2a8ce2af5ae403f813852957630d5a47ede36d2d0b`.

Production: совпадение исходной версии подтверждено до deployment; новый image создан overlay на точном прежнем image и проверен без сети. Source/environment/image backup и verified PostgreSQL dump сохранены приватно на сервере. API активирован, контейнер running/restarts=0. Внешний HTTPS capabilities=200/no-store, account с неизвестным bearer=401 и token refresh с неизвестным token=400 проверяют настоящий новый DB path. Existing login=200, support history без login=401. Реальные клиентские данные не запрашивались, тестовые аккаунты/сессии не создавались. [Production smoke](../tests/evidence/0.2.4/production-api.json).

| Проверка | Итог 0.2.4 |
|---|---|
| Core JVM | 29/29 PASS, failures/errors/skipped=0: импорт списков, старые display/routing checks, zoom, PKCE callback, release/ABI/security, bounded updater stream |
| Backend PostgreSQL / API / regression | 27/27 PASS: 14 новых API tests + existing support/payment-return, 6.036 s; disposable local PostgreSQL 17 остановлен |
| Debug / Release | PASS, STRICT Dependency Verification, R8/resource shrinking |
| Android test compilation | PASS; выполнение не заявляется |
| Android Lint | 0 errors / 48 warnings, lintVitalRelease PASS |
| GLSL ES shaders | PASS compile/link WebGL1; не Android GPU/FPS |
| Флаги | 257 статических PNG references: файлы присутствуют, 160×120; специальный AUTO рисуется нативно |
| 8K карты | SHA-256 и размеры 8192×4096 подтверждены; только bounded runtime crop 2048×2048 RGB565 |
| APK package/signature/alignment | PASS 0.2.4/code 6, три ABI, v2 signature, zip/ELF 16 KB |
| Cold cache / long CI / emulator / physical phone | NOT RUN в 0.2.4 по просьбе ограничить проверки |
| Реальный PackageInstaller self-update cycle | NOT RUN; проверены pure stream/hash/size/URL branches и Android compilation |
| Production API | PASS: verified backup, isolated image, migration, enabled HTTPS capabilities, DB-backed guards, existing login/support guards |
| Physical OAuth, live customer subscription/devices/bot delivery | NOT RUN; authenticated customer end-to-end ещё нужен |

Финальная Android сборка после изменений очистки installer session: 1m 9s. Lint warnings не подавлялись: рекомендации KTX/обновлений зависимостей и прежние замечания. Synchronous commit выполняется в Dispatchers.IO с проверкой результата до передачи управления установщику. Global verification exclusions/off не использовались, новые Maven зависимости не добавлялись.

Импорт исправлен: прежний parser принимал только Signet schemaVersion/type JSON; Happ/Incy экспорт пользователя — пакет на строку. Новый parser принимает этот формат, CSV/JSON array и документированные per-app headers, сохраняет strict validation собственного JSON. Повреждённая последняя строка с `22:01` показывается в preview исключённых строк, а не исправляется догадкой. Для plain list режим выбирается явно. Полный пользовательский app inventory не публиковался.

Account API проверяет owner через существующий billing_identity для Telegram-only и OAuth-only без обязательной Telegram-привязки; linked identity заново разрешается после подтверждённой привязки. Используются общие cabinet/bot rows и support services; fake balance/user ID из payload не принимаются. SQL tests проверяют idempotent autopay и чтение той же строки bot_data reader, token hashing, PKCE, one-time codes, refresh replay/concurrency, чужие subscriptions, общий support request_id и browser payment-return regression. Android хранит account credentials отдельно в AES-GCM/Keystore vault; новая серверная миграция содержит только две mobile таблицы. [Backend bundle summary](../tests/evidence/0.2.4/backend-api.json).

Self-update: один public ABI asset, HTTPS redirect allowlist, size и SHA-256 перед commit в PackageInstaller session. Системный installer проверяет expected package/signature/update и требует пользовательское подтверждение. APK-файла в Downloads/files/cache приложения нет; системная временная запись всё равно требуется. Abandon на отмене/ошибке; MY_PACKAGE_REPLACED и startup recovery очищают metadata/orphan sessions. Системные permissions/Play Protect не обходятся. Работоспособность полного жизненного цикла на физическом Android не заявлена.

Память: full 8K bitmap не загружается, два bounded GPU detail regions заменяются; CPU crop освобождается, cache коллекции стран нет. Фото увеличивает фиксированный размер APK примерно на 7.7 MB. Chat держит страницу 50 сообщений и одно фото, HTTP disk cache отсутствует; журнал остаётся 200 строк RAM. Профили/баланс/поддержка не копируются между отдельными базами; backend использует ту же БД кабинета/бота. Длительный profiler/soak не выполнялся.

Основные файлы: UI `SignetScreen`, `Preferences`, `RoutingAndProbeScreens`, `LocationList`, `CountryFlag`, `JsonEditor`, `MobileAccountScreen`, `UpdateWindow`; `MobileAccount`, `NativeUpdater`, `Vault`, manifest/activity/application/viewmodel; core `AppRoutingTransfer`, `MobileAuth`, `UpdateTransfer`, `AppRelease`, `GlobeGeometry`; `GlobeView`, 8K day/night assets и attribution; тесты/документация. Backend files и deployment guide перечислены в локальном `signet_cabinet/docs/MOBILE_API_V1.md`.

[Проверки](../tests/evidence/0.2.4/checks.json), [APK](../tests/evidence/0.2.4/apk-verification.json), [флаги](../tests/evidence/0.2.4/flags.json), [шейдеры](../tests/evidence/0.2.4/glsl-check.json). Локальный development-сертификат совпадает с 0.2.1–0.2.3: SHA-256 `8158ed1e88c0d5e485abed87d63209f295021a482fc6c51d108e44fdce0448b1`. Это не production signing и не гарантия отсутствия предупреждения Play Protect.

---

# Signet Android 0.2.3 — проверка 18.11.2026

Изменения: [RELEASE_0_2_3](RELEASE_0_2_3.md).

[Публичный выпуск v0.2.3](https://github.com/kirkanw88/signet-android/releases/tag/v0.2.3) опубликован из `e4c1232`. Три ABI APK, исходники и SHA256SUMS доступны; digest каждого asset совпал с локальным SHA-256. Точный endpoint приложения `/releases/latest` без Authorization вернул `v0.2.3`, draft=false/prerelease=false. [Подтверждение публикации](../tests/evidence/0.2.3/public-release.json). ARM64: 42 617 267 байт, SHA-256 `b0f78dbc0b0eb6a2917df9583fa04122b608005302ee90e222336d62bdbd781f`.
 Работа выполнена в `feature/android-0.2.0`; main и производственная инфраструктура не изменялись. Проверки ограничены изменёнными сценариями по просьбе пользователя; долгие эмуляторные/CI прогоны и cold-cache сборка не повторялись. Новых зависимостей нет, verification-metadata не менялся, Gradle Dependency Verification STRICT.

| Проверка | Итог 0.2.3 |
|---|---|
| AndroidDnsTest | 5/5 PASS: узкий перехват DNS, исходный JSON, DoH/hosts, правила, reuse/tag collision, AUTO/observatory members, TCP fallback/IPv6 |
| SubscriptionRequestTest | 3/3 PASS: явный /json, сохранение raw query/token, готовые JSON URL, запрет небезопасных URL |
| GlobeGeometryTest | 3/3 PASS: усиленное приближение без входа внутрь сферы, dateline, сферическая заливка |
| Настоящий Xray DNS | PASS UDP и TCP: runtime fixture с production adapter, виртуальный 172.19.0.2 → builtin hosts ответ 198.18.0.1; upstream намеренно недоступен |
| GLSL ES compile/link | PASS WebGL1; не тест Android GPU/FPS |
| Debug / Release | PASS, strict verification, R8/resource shrinking для Release |
| Android test compilation | PASS; выполнение НЕ заявляется |
| Android Lint | 0 errors / 39 warnings; lintVitalRelease PASS |
| APK identity/signature/alignment | PASS, 0.2.3/code 5, три ABI, v2 signature, zip/ELF 16 KB |
| Чистый cache / emulator / physical phone | NOT RUN в 0.2.3 |
| Пользовательская подписка / strict Android Private DNS | NOT RUN |

Первый успешный проход: целевые тесты + Release + компиляция Android tests + Lint, 1m 58s. После исправления цвета текста в полноэкранных панелях выполнена финальная сборка Debug/Release, Lint и компиляция Android tests, 1m 20s. Предшествующий unit assertion ожидал буквальное разворачивание selector, даже когда служебный tag уже не подходил под prefix; assertion исправлен на проверку фактического состава. Итог 11/11, failures/errors/skipped=0. Проверки безопасности не отключались.

Фактические дефекты: (1) запрос исходной ссылки с Accept не выбирал XRAY_JSON в Remnawave; явный `/:shortUuid/json` выбирает формат без подмены User-Agent под Happ/INCY; (2) числовой DNS/резервный DNS в VpnService не был связан со встроенным DNS Xray и DoH/hosts JSON. Новый узкий runtime adapter доставляет DNS пакеты Android встроенному модулю; защищённый bootstrap ядра привязан к физической сети. HTTPS health приложения остаётся через TUN и проверяет настоящий TLS. Это подтверждённые дефекты кода и локального контракта; точная работа пользовательского сервера на телефоне ещё не подтверждена.

Основные изменённые файлы: `Repository.kt`, `Settings.kt`, `SignetVpnService.kt`, `NativeCore.kt`, `AndroidDns.kt`, `SignetLinks.kt`, `Profile.kt`; UI `SignetScreen.kt`, `Preferences.kt`, `RoutingAndProbeScreens.kt`, `SpaceBackdrop.kt`, `ClipboardTransfers.kt`, `LocationList.kt`, `GlassButton.kt`; `GlobeView.kt`, `GlobeGeometry.kt`, offline `capitals.json` и provenance; целевые тесты, `scripts/check-dns-bridge.py`, документация и version 0.2.3.

Новый фон использует существующий RGB565 bitmap фиксированного размера на экран, без коллекции накопленных изображений. Маркер обновляет одно состояние, столицы — offline reference 193 записей. Журнал по-прежнему ограничен 200 RAM строками. Длительный memory/soak тест не выполнялся.

APK подписан тем же локальным development-сертификатом, что опубликованные 0.2.1/0.2.2: SHA-256 `8158ed1e88c0d5e485abed87d63209f295021a482fc6c51d108e44fdce0448b1`. Это не production signing и не подтверждение отсутствия предупреждения Play Protect.

[Результаты](../tests/evidence/0.2.3/checks.json), [native DNS](../tests/evidence/0.2.3/dns-bridge.json), [APK](../tests/evidence/0.2.3/apk-verification.json), [шейдеры](../tests/evidence/0.2.3/glsl-check.json). После обновления APK обновите подписку в шторке серверов для замены старого URI-формата фактическим JSON.

---

# Signet Android 0.2.2 — проверка 18.11.2026

Изменения: [RELEASE_0_2_2](RELEASE_0_2_2.md). Репозиторий сделан публичным по прямому запросу пользователя. Код остаётся в `feature/android-0.2.0`, main и производственная инфраструктура не изменялись.

[Публичный выпуск v0.2.2](https://github.com/kirkanw88/signet-android/releases/tag/v0.2.2) опубликован из `2f05091`. APK трёх ABI, исходники и SHA256SUMS загружены; digest каждого asset совпал с локальным SHA-256. Точный endpoint приложения `/releases/latest` проверен без Authorization: вернул `v0.2.2`, draft=false/prerelease=false и доступные APK. [Подтверждение публикации](../tests/evidence/0.2.2/public-release.json). Локальный и предыдущий APK 0.2.1 имеют сертификат SHA-256 `8158ed1e88c0d5e485abed87d63209f295021a482fc6c51d108e44fdce0448b1`.

Проверка ограничена новыми сценариями по просьбе пользователя: названия стран/AUTO/White, расчёт Солнца, реальный локальный TLS, повтор после DNS-сбоя, отклонение недоверенного сертификата и ограничение журнала. Длительные Android/emulator suites не повторялись. Шейдеры проверены compile/link в WebGL1; это не проверка Android GPU/FPS. Инструментальные тесты обновлены под новую навигацию и компилируются; их выполнение для 0.2.2 не заявляется. Результаты предыдущих версий ниже относятся только к своим версиям.

| Проверка | Итог 0.2.2 |
|---|---|
| Dependency Verification | STRICT, без новых исключений |
| DisplayAndSettingsTest | 10/10 PASS |
| SolarPositionTest | 2/2 PASS |
| ConnectionHealthTest | 4/4 PASS; настоящий localhost TLS |
| GLSL ES compile/link | PASS, WebGL1 |
| Release / Android test compilation | PASS; BUILD SUCCESSFUL 2m 50s, instrumented tests compiled only |
| APK identity/signature/alignment | PASS, versionName 0.2.2/code 4, 3 ABI, v2 signature, zip/ELF 16 KB |
| Physical phone / exact user subscription | NOT RUN |

Ошибка HTTPS: в 0.2.1 использовался `response.isSuccessful`, что принимало только 2xx и отвергало корректные HTTPS-ответы 403/503. Целевой TLS-тест воспроизводит этот сценарий и подтверждает исправление. Сертификаты проверяются, plain HTTP отклоняется. Проверка идёт через TUN, TCP-пинг не заменяет её; URL измерения остаётся gstatic, health URL отдельный. Установить точную причину отказа конкретной подписки без подробной ошибки/конфигурации не удалось; пользователь подтвердил работу сервера в другом клиенте. Новый журнал и диагностика показывают этап и безопасный код ошибки.

Логирование выключено по умолчанию, включается в настройках; ограничено 200 строками в памяти. Тест проверяет ограничение размера, очистку и отклонение URL/UUID. Перезапуск процесса освобождает журнал. Копирование/экспорт выполняются пользователем. Ключи, HWID, конфигурации и адреса узлов не записываются. Фото Земли: NASA/three.js; звёздный фон: imagegen, [точный промпт и происхождение](../assets/space-source.json).

Историческое уточнение 0.2.1: третий cold build [44300945435](https://github.com/kirkanw88/signet-android/actions/runs/44300945435) прошёл сборочную часть, но runtime был остановлен до запуска эмулятора проверкой свободного диска: 3.2 GB при требуемых 4 GB. Для будущего стенда добавлено удаление только собственного Gradle runtime-cache после завершения strict-сборки, с проверкой пути внутри репозитория. Эта настройка не объявляется проверенной новым долгим прогоном.

---


[Целевые проверки](../tests/evidence/0.2.2/checks.json), [APK](../tests/evidence/0.2.2/apk-verification.json), [шейдеры](../tests/evidence/0.2.2/glsl-check.json).

# Signet Android 0.2.1 — проверка 18.11.2026

Код приложения `177f860`, стенд `a40e962`, ветка `feature/android-0.2.0`. Исправления и причины: [RELEASE_0_2_1.md](RELEASE_0_2_1.md). Production, соседний кабинет и main не изменялись. Старые результаты 0.2.0/0.1.0 ниже относятся только к своим версиям.

| Проверка | Результат 0.2.1 |
|---|---|
| Local core JVM | 45/45 PASS, failures/errors/skipped = 0 |
| app JVM | NO-SOURCE |
| Android Lint Debug | PASS, 0 errors / 38 warnings |
| Debug / Release / test APK | PASS, strict Dependency Verification, R8/resource shrinking |
| APK integrity | PASS, три ABI, versionName 0.2.1/code 3, Signature v2, zip/ELF 16 KB alignment |
| Cold CI build | PASS, runs 44295197199 и 44298413902: 45 JVM tests, Lint 0 errors / 38 warnings, Debug/Release, APK integrity |
| Android emulator | Полная приёмка НЕ ПОДТВЕРЖДЕНА: первый run — 2 PASS / 4 FAIL (System UI ANR); второй — падение host emulator; native NOT RUN |
| Physical ARM / camera / soak | NOT RUN |

Локальная полная проверка: `--dependency-verification strict --no-daemon :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest`, BUILD SUCCESSFUL за 4m 38s. [Результаты](../tests/evidence/0.2.1/local-checks.json), [проверка выдаваемых APK](../tests/evidence/0.2.1/apk-verification.json). Глобальные исключения и отключение Dependency Verification не применялись; зависимости не добавлялись.

[Cold CI build](../tests/evidence/0.2.1/ci-build.json): сборка Ubuntu с новым `GRADLE_USER_HOME` и без restore cache, strict verification. [GitHub Actions 44298413902](https://github.com/kirkanw88/signet-android/actions/runs/44298413902).

Первый эмуляторный прогон не прошёл: [снимок System UI ANR](../tests/evidence/0.2.1/emulator-attempt-1/system-ui-dialog.png), [вывод UI-тестов](../tests/evidence/0.2.1/emulator-attempt-1/ui-instrumentation.txt). Bounds/QR assertions прошли, но четыре UiSmokeTest остановились в ожидании фокуса: поверх Signet находился системный диалог. `lastanr` ошибочно не отражал видимый сбой, поэтому не используется как единственная проверка. Workflow `7f214c7` добавляет проверку системных диалогов до/после тестов, прогрев Settings после загрузки и logcat; устаревший `swiftshader_indirect` заменён поддерживаемым `swiftshader` ([Android documentation](https://developer.android.com/studio/run/emulator-acceleration)). Это изменение стенда, а не подтверждение исправления Android System UI на всех устройствах; причина его внутреннего ANR не установлена. Приложение не изменялось, проверки не отключались.

Во втором прогоне проверка готовности Settings прошла. Host-процесс эмулятора упал с segmentation fault во время GlobeVisualTest, одновременно runner сообщил `Free space left: 0 MB`; [результат](../tests/evidence/0.2.1/emulator-attempt-2/summary.json). Связь заполнения диска и падения не установлена. `a40e962` ограничивает userdata AVD до 2 GB, запрещает core dump, проверяет свободные 4 GB до запуска и использует поддерживаемый автоматический выбор software backend. Повтор после этой настройки ещё не засчитан успешным. Код приложения и выдаваемые APK не менялись. Результаты 17/17 Android tests для 0.2.0 ниже не переносятся автоматически на 0.2.1.

Причины: Home раньше содержал список и ограниченную область планеты; теперь список на своей вкладке, а планета и поля занимают отдельные области. Стеклянные кнопки используют градиент и кромку без blur buffers. Текст метаданных проходил через `DisplayText.clean`, который схлопывал пробелы и переносы; новый sanitizer сохраняет форматирование. HWID зависел от сохранённого opt-out; теперь загрузка/сохранение принудительно включают его, запрос не зависит от флага.

HTTPS при подключении ошибочно брал `probes.url`, независимо от TCP-режима. Например, Remnawave split блокирует прежний gstatic через `geosite:google`. Добавлен отдельный `connectionCheckUrl`, по умолчанию example.com, с редактированием в настройках. Проверка настоящего HTTPS через TUN обязательна; TLS и правила профиля сохранены. Начало подключения отменяет параллельные пробы.

QR: normal-only decoder, несовпадающая декоративная рамка и остановка после неподходящего кода ограничивали сканер. Mixed decoder, непрерывный автофокус и продолжающееся сканирование исправляют эти пути. Image import добавляет inverted/binarizer fallback; известные launcher-ссылки раскрываются в безопасный HTTPS без изменения токена. Конкретный пользовательский QR отсутствует, поэтому успешное чтение именно его на физической камере не заявляется.

APK ARM64: `release/signet-0.2.1-arm64-v8a.apk`, SHA-256 `bea7f70f92167c3c34353a666bf71052f590c80d482b75c09449ce8f4d516889`. Прежний локальный development key; CI использует другой development key. Публичная публикация обновлений и production signing этой задачей не выполнялись.

---

# Signet Android 0.2.0 — итог проверки 18.11.2026

Проверенный commit `93769e7`, функциональный код APK — `291e4ce`. Ветка `feature/android-0.2.0`, база `fc4f885`. [GitHub Actions 44288047516 — SUCCESS](https://github.com/kirkanw88/signet-android/actions/runs/44288047516): оба job завершились успешно. Production Signet, соседний кабинет и main не изменялись. Ниже сохранён исторический отчёт 0.1.0.

| Проверка | Фактический итог |
|---|---|
| Dependency Verification | STRICT, чистый Gradle cache Ubuntu, без restore и без исключений |
| Core JVM | 42 tests, 0 failures/errors/skipped |
| app JVM | NO-SOURCE, не засчитывается отдельным тестом |
| Android Lint Debug | PASS, 0 errors / 39 warnings |
| Debug / Release | PASS, R8 и resource shrinking включены |
| APK integrity | Три ABI, versionName 0.2.0/code 2, Signature v2, zip/ELF 16 KB alignment, корректный ELF machine |
| Android API36 x86_64 | 17/17 PASS: 6 UI/QR/graphics + 11 native/storage/format tests, без skipped |
| Release install / responsive | PASS, 7/7 размеров/шрифтов; основной CTA внутри экрана |
| Страны RU/DE/FI/JP/TR | Готовые GL-кадры сохранены и просмотрены; uniform fill, контрастный city marker |
| GLES shaders | Точные строки compile/link WebGL1 PASS; Android кадры также получены |
| Host Xray | 9/9 e2e fixtures PASS |
| Emulator ANR | `no ANR has occurred since boot`; проверяется workflow |
| Физические ARM телефоны / soak | NOT RUN |

Доказательства: [CI build summary](../tests/evidence/0.2.0/ci-build.json), [Android summary](../tests/evidence/0.2.0/runtime-final.json), [инструментальный вывод](../tests/evidence/0.2.0/android-final/instrumentation.txt), [снимки](../tests/evidence/0.2.0/android-final/), [локальные APK SHA/signature/ABI](../tests/evidence/0.2.0/apk-verification.json). CI использует собственный development key; его APK hashes отличаются от выдаваемых локальных файлов.

## Что подтверждено

Настоящий Android VpnService TUN: VLESS TCP, REALITY Vision, XHTTP REALITY, XDrive template, MASQUE HTTP/2 и HTTP/3 CONNECT-IP. Проверяются CONNECTED после HTTPS, передача TCP/HTTPS, UDP DNS, изолированная проверка ядром при работающем VPN и неизменность исходного JSON. Native API/форматы и encrypted Room storage также прошли проверки. Hysteria2 подтверждён только conversion-тестом, его e2e NOT RUN.

По выбору пользователя проверены типовые Remnawave конфигурации: upstream default после добавления host outbound, AUTO с balancer и split direct/proxy/block. Android split проверяет GeoIP private/direct, geosite Google/gstatic block и явный domain block. Production-подписки не запрашивались. Исправлен перенос ingress sniffing в TUN/probe; правила, DNS, outbounds и balancers сохраняются. IPIfNonMatch + catch-all диагностируется предупреждением, без переписывания политики. Raw injector template не выдаётся за сформированную подписку.

Выявлена практическая причина отказа split health check: geosite:google блокирует gstatic. Fixture теперь явно выбирает разрешённый HTTPS example.com, сохраняя блокировки; пользователь может выбрать адрес в Ping settings. Проверка TLS/HTTPS не отключалась. Подробности — REMNAWAVE_TEMPLATE_COMPATIBILITY.md.

UI тесты подтверждают навигацию/recreate, реальное касание основной кнопки при временном уведомлении, открытие/отмену импорта, доступность «Проверить обновление», показ и скрытие баннера доступного выпуска на контролируемых metadata. QR PNG действительно декодируется. Ручные Settings actions привязаны к update client, Ping, template checker и account. Snackbar поднят над основным CTA и не переигрывает старое сообщение после disposal. Permission dialogs в fixture предвыданы, поэтому первый пользовательский consent-flow не объявляется проверенным.

Release устанавливается после Debug и проверяется на 360×640, 393×851, 412×915, 600×960, 800×1280, 640×360 и 360×640/font 1.3. Это проверка Home/CTA и снимков, не полная приёмка каждого экрана во всех состояниях. Страновые кадры сняты в DISCONNECTED: connected zoom/FPS на телефоне отдельно NOT RUN. Native TUN suite выполняется в Debug; именно Release VPN e2e отдельно NOT RUN.

## Память и файлы

APK: arm64 41,907,511 bytes; ARMv7 41,183,517; x86_64 44,284,718. Устанавливается один файл нужной архитектуры. Локальные 0.1.0/0.2.0 имеют одинаковый сертификат; [проверка](../tests/evidence/0.2.0/local-upgrade-signature.json).

Изолированный debug instrumentation с активным VPN и закрытым UI: 204515–211360 KiB PSS (около 200–206 MiB), шесть профилей. Release Home без VPN: 41199 KiB PSS (около 40.2 MiB). Это разные сценарии; один снимок не задаёт предел RAM и не доказывает отсутствие утечки. [Raw snapshots](../tests/evidence/0.2.0/android-final/vpn-memory/), [Release Home](../tests/evidence/0.2.0/android-final/meminfo-release.txt).

Ограничения: 4 MB import, 8 subscriptions/500 profiles/8 MB рабочих configs; QR 16 MB input с downsampling до ~1536 px; metrics 512 KB; update metadata 512 KB с одним compact cache. Probes максимум 5 параллельно/1–3 попытки, результаты не пишутся на диск. Нет traffic file logs, HTTP disk cache и auto-download APK. Ночная GPU texture 1024×512 RGB565, bitmap освобождается; GL приостанавливается в фоне. Source zip содержит только tracked files, без локальных ключей, AAR и Gradle cache. Подробности — PERFORMANCE.md.

## Границы выпуска

- Public GitHub updater реализован, но репозиторий закрыт и публичного Release нет. Availability BLOCKED до публикации владельцем; токен в APK не встроен, visibility не менялась. Следующий APK обязан иметь тот же signing key.
- Native account/session/billing API отсутствует. Работают переходы в существующий browser кабинет/Google/Яндекс OAuth/оплату/поддержку; native часть BLOCKED, не имитируется. Предложенный контракт API_CONTRACTS.md не развёрнут.
- Реальный Google Drive OAuth/S3/provider XDrive, публичный MASQUE, IPv6 egress, MTU/loss, физическая камера/launcher matrix, per-app/work-profile acceptance, Wi-Fi/mobile/reboot/lockdown, батарея/FPS и длительный soak: NOT RUN.
- Development certificate, loopback metrics без UID isolation и доверие произвольному provider JSON остаются ограничениями. Formal CVE/pentest audit не выполнен. Нет обещаний универсального обхода DPI.

История неудачных попыток сохранена в STAGE_RESULTS_0_2_0.md и runtime-first/second/third.json. Ошибки включали adb PATH, AVD lookup, запрещённый health URL, непривязанные UI actions и System UI ANR малой VM. Последний прогон разделяет UI/release и native stress, использует 4 GB AVD, освобождает compiler RAM и проверяет отсутствие ANR. Проверки не удалялись и не подменялись успешными результатами.


## Исторический отчёт Signet 0.1.0 — 17.11.2026

Xray/libXray v26.11.9, Kotlin/Compose, native Android VpnService TUN. Это устанавливаемая первая development release с локальной подписью разработки. Полная приёмка исходного ТЗ ещё не завершена.

## Исправление GitHub Actions / Dependency Verification

Исходный сбой подтверждён логом [Actions run 44171366263](https://github.com/kirkanw88/signet-android/actions/runs/44171366263), commit `143e04d41c3808916ea9193dcbe60bfaea820374`: `:app:javaPreCompileDebug`, конфигурация `:app:_agp_internal_javaPreCompileDebug_kaptClasspath`, артефакт `com.google.guava:guava-parent:33.2.1-jre`, репозиторий `MavenRepo`.

Причина: в `gradle/verification-metadata.xml` полностью отсутствовала запись `guava-parent-33.2.1-jre.pom`. Записи других версий Guava и checksum для JAR/module самой Guava не распространяются на parent POM. Это отсутствие checksum, а не несовпадение существующей суммы. `MavenRepo` соответствует `mavenCentral()` из settings.gradle.kts; признаков неожиданного репозитория в этом сбое нет. Репозитории остались google()/mavenCentral() и Gradle Plugin Portal для плагинов, с FAIL_ON_PROJECT_REPOS.

Путь зависимости подтверждён Gradle metadata/POM: `kapt(androidx.room:room-compiler:2.7.1)` → `androidx.room:room-compiler-processing:2.7.1` → `com.google.guava:guava:33.2.1-jre` → parent `com.google.guava:guava-parent:33.2.1-jre`. Локальный прогон с исходным XML и новым dependency cache воспроизвёл ошибку, HTML-отчёт явно указал `Checksums are missing from verification metadata`; извлечённая таблица сохранена в `tests/evidence/dependency-verification-before.txt`. Фактически скачанный Gradle POM совпал с проверенным Central SHA-256.

POM получен по HTTPS с `repo.maven.apache.org/maven2` и `repo1.maven.org/maven2`: 19431 байт, содержимое совпадает. Вычисленный SHA-256: `9095f6d8ee3765950207510785830cc2280e781067915fa99cfe1740d4865c14`. Дополнительно проверено совпадение с опубликованным Central SHA-1 `fc55955fab23e86fad230acc81bf570deb5660bd`. Sidecar `.sha256` для этого POM возвращает 404, поэтому SHA-256 вычислен из подтверждённых HTTPS-байтов, а не назван опубликованным Central SHA-256. Источники и значения: `tests/evidence/central-metadata.json`. Повторяемая проверка без изменения доверия: `python scripts/verify-central-metadata.py com.google.guava:guava-parent:33.2.1-jre guava-parent-33.2.1-jre.pom`.

Дополнительно найдена отсутствующая Linux-вариация закреплённого AAPT2 `com.android.tools.build:aapt2:8.10.1-12782657`, необходимая Ubuntu runner; ранее XML содержал только Windows JAR. Linux JAR получен с официального Google Maven, повторно через maven.google.com с переходом на dl.google.com; байты и опубликованный SHA-1 совпали. SHA-256 `52f864b7fd20a9ff09fc3db96162537a63c5b38ecc1c2549db4b491c6a517ff0`. Источники: `tests/evidence/google-metadata.json`. Это конкретный OS-артефакт, а не разрешение всем зависимостям Google Maven.

Добавлены только эти подтверждённые записи. `verify-metadata=true` сохранён, существующие суммы не заменены, trusted-artifacts / глобальные исключения не добавлены. Автоматическое дописывание checksum через `--write-verification-metadata` и отключение verification не применяются.

Workflow теперь явно запускает strict verification, `:core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`, использует отдельный `${{ github.workspace }}/.gradle/ci-user-home` без восстановления старого кэша и сохраняет диагностические отчёты при сбое. Локальная проверка использует новый `.gradle/ci-verification-home`; старый dependency cache не копируется. Установленный Gradle 8.13 используется как инструмент, независимо от нового dependency cache. Сетевой прокси задан только в локальном окружении процесса, в репозиторий его настройки не включены.

Проверка чистого окружения завершена 18.11.2026 (Europe/Moscow): [Actions run 44174008896 — SUCCESS](https://github.com/kirkanw88/signet-android/actions/runs/44174008896), commit `62e9190aa78a0f628aa06b42263541945805a385`. Gradle: `BUILD SUCCESSFUL in 8m 49s`. В логе подтверждён новый `GRADLE_USER_HOME=/home/runner/work/signet-android/signet-android/.gradle/ci-user-home`, без restore dependency cache.

| Проверка в новом Ubuntu 24.04 runner | Результат |
|---|---|
| Dependency Verification, strict | PASS, включая проблемную javaPreCompileDebug/KAPT configuration |
| `:core:test` | 10 tests, 0 failures/errors/skipped; XML: `tests/evidence/ci-dependency-unit-tests.xml` |
| `:app:testDebugUnitTest` | NO-SOURCE; дополнительных JVM-тестов в app нет |
| `:app:lintDebug` | PASS, 0 errors, 29 warnings |
| `:app:assembleDebug` | PASS, APK всех настроенных ABI созданы |
| `:app:assembleRelease` | PASS, R8 + resource shrinking, APK созданы |

[APK из этого прогона](https://github.com/kirkanw88/signet-android/actions/runs/44174008896/artifacts/14369252538) и [исходные отчёты тестов/Lint](https://github.com/kirkanw88/signet-android/actions/runs/44174008896/artifacts/14368519352) загружены Actions. Фильтрованный журнал шага сборки: `tests/evidence/ci-dependency-build.txt`. Все 1063 прежние записи артефактов сохранены; добавлены ровно две записи, описанные выше. Других отсутствующих checksum в полном Ubuntu-прогоне не выявлено.

Локальный полный прогон Windows завершился `BUILD SUCCESSFUL in 11m 30s`, 110 tasks (108 executed, 2 up-to-date). Использованы `clean`, `--no-build-cache`, отдельный новый dependency cache и те же strict-задачи. Начальный прогон для воспроизведения заполнил этот новый кэш; старый Gradle dependency cache не копировался. Unit: 10 tests, 0 failures/errors/skipped. Lint: 0 errors, 29 warnings. Debug и Release успешно собраны, в сумме 8 APK (три ABI + universal для каждого build type). Журнал задач: `tests/evidence/local-dependency-build.txt`, размеры/SHA-256 APK: `tests/evidence/local-dependency-apks.json`. ARM64 Release: `app/build/outputs/apk/release/app-arm64-v8a-release.apk`, 41052886 байт, SHA-256 `c71e6481f455a20571fefa5b2cdb4049a4b87f6b0d4164fed043fc244f546d3a`; apksigner verification и `zipalign -c -P 16 4` прошли. Подпись остаётся development/debug, как в исходной конфигурации. Установка/инструментальные проверки в этом исправлении CI не выполнялись.

Для старого установленного JDK 17.0.1 используется существующий ASCII junction `C:\Android\signet-build`, проверенно указывающий на этот же репозиторий; файлов проекта вне запрошенного репозитория не создавалось. Чистое разрешение зависимостей и сборка через wrapper на Ubuntu независимо подтверждены run выше.

Первый промежуточный push был отклонён валидатором Actions до создания job из-за `runner.temp` в job-level env. Это исправлено на разрешённый там `github.workspace`; успешный run выше включает исправление. Ошибок сборки после добавления двух подтверждённых checksum в Ubuntu не осталось. Исторические проверки приложения ниже не означают повторного запуска UI/инструментальных тестов в рамках этого исправления CI.

## Подтверждено

| Проверка | Результат | Доказательство |
|---|---|---|
| Release build, R8, ABI splits | PASS | `tests/evidence/build-final.txt` |
| Core unit tests | 10 PASS, 0 failures | `tests/evidence/unit-tests.xml` |
| Android Lint | 0 errors, 29 warnings | `tests/evidence/lint.txt` |
| Подпись ARM64 APK | PASS, APK Signature Scheme v2 | `tests/evidence/apk-signature.txt` |
| Выравнивание ZIP 16 KB | PASS | `zipalign -c -P 16 4` |
| Интеграционные тесты debug, предыдущий запуск | 5 PASS | Зафиксировано во время разработки; сырой общий вывод последующего запуска был перезаписан |
| UI tests, предыдущий запуск | 1 PASS / 1 FAIL | Исправлена синхронизация диалога; повторная проверка BLOCKED |
| Повтор полного набора и установка финального release | BLOCKED | Эмулятор после перезапуска не загружает Android / ADB offline |
| Матрица размеров и RAM финального release | BLOCKED | Та же неисправность стенда |

Предупреждения Lint включают закреплённые более ранние версии библиотек, размер и сложность векторной трассировки логотипа. Это не формальная проверка уязвимостей. Проверка ZIP-выравнивания не заменяет проверку ELF всех ABI на реальном 16 KB устройстве.

## Интеграционная среда

API 36 x86_64 emulator; локальный официальный Xray 26.11.9 слушает только host loopback. ADB reverse перенаправляет тестовые порты 10888–10890. Виртуальный путь 10.0.2.2 давал ненадёжные REALITY-соединения, поэтому для этих локальных тестов использован 127.0.0.1 через ADB. Это особенность стенда, не изменение пользовательских адресов в приложении.

Тесты проверяют преобразование VLESS REALITY, XHTTP TLS и Hysteria2 URI ядром; отсутствие тестового секрета в файлах Room; три полных подключения VLESS, REALITY/Vision и XHTTP REALITY. Для каждого сетевого сценария проверяются HTTP-проба до TUN, запуск native ядра, получение HTTPS через Android VPN, ненулевые счётчики и изолированная проба при активном VPN. DNS используется этими запросами, отдельный полный аудит утечек DNS не выполнен.

UI tests проверяют навигацию, восстановление Activity и отмену импорта. Предыдущая проверка отмены импорта завершилась assertion failure; ожидание появления диалога и проверка возвращения к подпискам исправлены в тесте, но успешный повтор не получен. Более ранний сырой вывод сохранён как `tests/evidence/ui-earlier.txt`. `tests/evidence/instrumentation-status.txt` фиксирует границы подтверждения. Финальную сборку нельзя объявлять прошедшей все семь тестов. Debug APK ранее устанавливался и работал; установка именно финального release после R8 пока не повторена.

## Статическая проверка

Полный JSON сохраняется, конфигурации шифруются Android Keystore AES-GCM, подписки ограничены HTTPS и размером, ссылки требуют подтверждения импорта. Нет production токенов и изменений инфраструктуры. Проверены перечни лицензий и источники native/assets. Формального пентеста и CVE-аудита не было. Подробности — SECURITY.md.

Логотип трассирован из пользовательского оригинала в прозрачные векторы. Границы — Natural Earth, геометрия Polygon/MultiPolygon с отверстиями. Сфера, освещение, атмосфера, облака и анимация реализованы GLES; снимки показывают внешний вид, но не доказывают все переходы анимации и точность всех полигонов мира.

## Ограничения / NOT RUN

- Физические ARM64/ARMv7 телефоны и Android 8–15: не проверены.
- Production Remnawave подписка, конкретный HWID-контракт и реальное продление: не проверены; пользовательская подписка не предоставлялась, production не затрагивался.
- Hysteria2: URI разбирается закреплённым ядром; end-to-end сервер не проверен.
- Произвольные balancers/routing, пользовательские DNS, IPv6 egress и широкая UDP-совместимость требуют отдельной матрицы. Наличие IPv6 TUN маршрутов не подтверждает весь IPv6 egress.
- Per-app include/exclude, reboot/Always-on/lockdown, переход Wi-Fi/mobile, длительный фон, батарея и длительные циклы reconnect: нет полной проверки на телефоне.
- AGE реализован с пользовательским ключом; закрытые криптоссылки конкурентов, их private APIs и отдельный менеджер custom geodata не реализованы.
- Нет полного паритета с Incy/Happ; сравнение публичных функций находится в FEATURE_PARITY.md.
- Производственный ключ подписи и публикация отсутствуют; перед публичным релизом обязательны собственный keystore и проверки на физических устройствах.

Память, ограничения накопления данных и пределы измерений: PERFORMANCE.md. Совместимость и размеры окон: DEVICE_MATRIX.md.
