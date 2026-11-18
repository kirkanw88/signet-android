# Результаты этапов 0.2.0

## A — критические функции

Strict verification, core unit tests (15 tests), app JVM task (NO-SOURCE), Lint (0 errors, 29 warnings), Debug/Release: PASS. Повтор после добавления refresh-regressions: BUILD SUCCESSFUL in 1m 58s. Исходный лог: локальный `.gradle/upgrade-audit/stage-A-final.log`; итоговые доказательства будут собраны в tests/evidence/0.2.0.

Удалён выбор AUTO по пингу. AUTO — только доставленный JSON с точным remarks AUTO либо явным signet.selectionMode=auto. Это документированный контракт клиента; отдельное metadata signet не объявляется стандартом Remnawave. Сохраняются outbounds/routing/DNS/balancers, включая untagged outbounds. Refresh сохраняет явный profileId и одиночный AUTO, удаление AUTO не выбирает обычный сервер. Одинаковые имена/конфигурации не объединяются.

Сканер — отдельная неэкспортированная Activity, явный runtime CAMERA, lifecycle pause/resume, защита от повторного распознавания, фонарик, отказ, QR из ограниченного по разрешению изображения, проверка содержимого и подтверждение импорта. Старый camera crash не воспроизведён без устройства/Logcat; аппаратная проверка NOT RUN, нельзя объявлять её PASS на основании сборки.

VPN: явный выбор переключает активный профиль; HTTPS-проверка выполняется через системный TUN, затем ограниченный heartbeat, до трёх retry. Во время аварии сохраняется существующий TUN до замены/явного отключения. Always-on/lockdown остаются отдельной системной защитой. Android runtime-регрессия ещё ожидает стенда.
