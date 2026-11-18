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
