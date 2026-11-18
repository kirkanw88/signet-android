# Результаты этапов 0.2.0

## A — критические функции

Strict verification, core unit tests (15 tests), app JVM task (NO-SOURCE), Lint (0 errors, 29 warnings), Debug/Release: PASS. Повтор после добавления refresh-regressions: BUILD SUCCESSFUL in 1m 58s. Исходный лог: локальный `.gradle/upgrade-audit/stage-A-final.log`; итоговые доказательства будут собраны в tests/evidence/0.2.0.

Удалён выбор AUTO по пингу. AUTO — только доставленный JSON с точным remarks AUTO либо явным signet.selectionMode=auto. Это документированный контракт клиента; отдельное metadata signet не объявляется стандартом Remnawave. Сохраняются outbounds/routing/DNS/balancers, включая untagged outbounds. Refresh сохраняет явный profileId и одиночный AUTO, удаление AUTO не выбирает обычный сервер. Одинаковые имена/конфигурации не объединяются.

Сканер — отдельная неэкспортированная Activity, явный runtime CAMERA, lifecycle pause/resume, защита от повторного распознавания, фонарик, отказ, QR из ограниченного по разрешению изображения, проверка содержимого и подтверждение импорта. Старый camera crash не воспроизведён без устройства/Logcat; аппаратная проверка NOT RUN, нельзя объявлять её PASS на основании сборки.

VPN: явный выбор переключает активный профиль; HTTPS-проверка выполняется через системный TUN, затем ограниченный heartbeat, до трёх retry. Во время аварии сохраняется существующий TUN до замены/явного отключения. Always-on/lockdown остаются отдельной системной защитой. Android runtime-регрессия ещё ожидает стенда.

## B — серверы, диагностика, маршрутизация приложений

Strict verification, 21 core tests, app JVM (NO-SOURCE), Lint (0 errors, 37 warnings), Debug/Release: PASS, 2m 6s. Локальный журнал `.gradle/upgrade-audit/stage-B-final.log`. Для увеличившегося Compose-кода heap Kotlin compiler поднят с 512 МБ до 1 ГБ; runtime Android это не меняет.

Оба списка используют LazyColumn с группами, сворачиванием, отдельными доставленными AUTO, подсветкой выбранного/подключённого, поиском и избранным. ServerDisplayModel отделён от исходного JSON и ID. 257 локальных флагов MIT flag-icons, pinned commit и hashes в assets/flags-source.json; статические R-ссылки сохраняются R8. Ресурсы Happ/Incy не использованы.

Четыре режима проб: TCP/HTTPS — отдельная доступность endpoint; Xray — полный профиль; outbound — изолированный pingBatch. AUTO всегда проверяется полным профилем с исходными DNS/routing/balancers. Пробы не меняют выбранный сервер. Таймаут, попытки, интервал, лимит параллельности, URL и opt-in проверки выбранного сервера на переднем плане; отмена освобождает процесс. Реальная сеть новых режимов ещё требует runtime-регрессии.

Список приложений: видимые PackageManager приложения, иконки только видимых строк, название/package search, системные, сортировка, выбор всех/снятие, три режима. Расширены адресные queries, включая Яндекс, QUERY_ALL_PACKAGES не запрошен. Android может скрывать остальные пакеты; рабочий профиль требует экземпляра Signet в этом профиле. Версионированный JSON переносит только режим/package names, с preview и счётчиком отсутствующих; секреты не экспортируются.

## E — Remnawave / маршрутизационные шаблоны (перенесён вперёд по уточнению пользователя)

Strict verification, 26 core tests, app JVM NO-SOURCE, Lint 0 errors / 39 warnings, Debug/Release: PASS, 3m 4s. Локальный журнал stage-E-routing.log.

Добавлены разбор официальных headers (announce, profile-web-page-url, support-url, profile-update-interval, refill-date), meta.serverDescription и безопасный текст/HTTPS links; сообщения над списком, формат JSON/URI, ошибки обновления без удаления старой подписки. HWID не передаётся на иной host при redirect. Периодическое обновление учитывает интервал, ручное — всегда. Изменения сначала записываются в encrypted vault и затем публикуются в StateFlow, ошибка загрузки vault запрещает его перезапись.

Проверка шаблона — статические refs/tags + isolated native validation. Приём metadata ядром и реальная маршрутизация различаются. Подробный аудит и ограничения — REMNAWAVE_TEMPLATE_COMPATIBILITY.md. Unit проверили сохранение DNS/routing/outbounds/balancers и правильную адаптацию TUN при первом DNS-inbound. Конкретные production-шаблоны Signet пока не предоставлены: NOT RUN.

## F — аккаунт / платежи и обновления

Strict verification, 31 core tests, app JVM NO-SOURCE, Lint 0 errors / 40 warnings, Debug/Release: PASS, 2m 31s. Журнал stage-F-updates.log.

Ручная проверка обновлений, foreground auto-check не чаще 24 ч, ETag/304, bounded metadata/cache, числовой SemVer, выбор ABI и баннер с отложенным показом. Только официальный GitHub repository, HTTPS, без токенов/cookies/HWID и фоновой загрузки APK. Unit проверяют версии, ABI, unsafe URLs, draft/prerelease. Для работы реальных обновлений необходим публичный Release; текущий private repository не открывался и токен в APK не добавлялся.

Публичный OpenAPI кабинета проверен GET-запросом. Настроены реальные browser OAuth Google/Яндекс, кабинет покупки/продления, поддержка и официальный бот. Native session API/schema отсутствуют: native login/registration/session refresh/device billing endpoints не имитируются и не объявляются готовыми. API_CONTRACTS.md содержит найденные поверхности и явно отделённый проект недостающего backend-контракта. Серверные изменения/входы/реальные платежи не выполнялись.

## C — графика

Strict verification, 34 core tests, app JVM NO-SOURCE, Lint, Debug/Release: PASS, 2m 32s, stage-C-final.log. Точные shader strings дополнительно compiled/linked в WebGL1/OpenGL ES2 Chromium: PASS, tests/evidence/0.2.0/glsl-check.json. Это не измерение Android FPS/GPU.

Заливка — один непрозрачный цвет без country glow/pulse. JTS-треугольники дополнительно разделяются перед проекцией на сферу: большие хорды больше не проваливаются под поверхность. Сохранены MultiPolygon/holes; кольца отверстий выравниваются по longitude shell. Добавлены плавный ограниченный zoom по угловому радиусу страны, возврат к overview, сохранение ручного вращения при потере GL context, детерминированный звёздный фон и настоящая ночная карта NASA Black Marble 2016. Ночная текстура при загрузке ограничена 1024×512 RGB565; CPU bitmap освобождается. High запрашивает 4x MSAA с fallback, Balanced/Battery не получают дополнительные multisample buffers.

Adaptive icon теперь использует знак И полную надпись SIGNET, вписанную в центральную safe area. Уведомление использует отдельный mono icon. Проверка физических launchers/нагрева/батареи: NOT RUN; подробная Android UI/географическая матрица ещё ожидает runtime-стенда.
