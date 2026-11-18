# Xray JSON-шаблоны Remnawave и Android

Аудит upstream Remnawave backend: commit `6853666801d6d1925b6aec55824106100485777a`, generator `src/modules/subscription-template/generators/xray-json.generator.service.ts` и `RemnawaveInjectorSchema`. Это не утверждение о версии production-панели Signet. Production templates / пользовательская подписка не предоставлены и не извлекались с помощью административных credentials.

## Что получает клиент

Генератор выдаёт массив готовых Xray-конфигураций. Для обычного host добавляет его outbound перед outbounds базового шаблона. Для `remnawave.injectHosts` сначала выбирает host, создаёт outbounds/tags, затем удаляет служебное поле remnawave из результата. remarks и, для расширенных клиентов, meta.serverDescription поступают отдельно. Исходный panel template не равен готовой подписке.

Signet сохраняет готовые outbounds (в том числе неизвестные поля и порядок), routing.rules, balancers, DNS, observatory и параметры транспортов. Не повторяет injector, не создаёт AUTO, не сортирует outbounds/rules, не заменяет balancer выбранным по Ping узлом. Raw template с remnawave отклоняется с объяснением необходимости получить сформированный JSON от панели. Подписка из share URI не содержит клиентский JSON-шаблон; восстановить отсутствующий DNS/routing из URI приложение не может. Формат подписки показан в UI.

## Адаптация Android

Оригинальные слушающие inbounds не открываются на устройстве. Один системный TUN получает tag исходного tun, иначе socks, иначе http, иначе signet-tun. Явный signet.tunInboundTag — расширение контракта Signet для неоднозначного шаблона, не стандарт Remnawave. Полные DNS/routing/outbounds/balancers не изменяются. Правила, ограниченные другими inboundTag, остаются в JSON, но не получают TUN-трафик: UI сообщает INBOUND_SCOPE. Автоматического переписывания этих правил нет.

Первый числовой DNS из dns.servers используется и как системный Android DNS. Для DoH/domain-only списка остаётся резервный IP из настроек; его запросы идут через TUN и правила шаблона. Наличие DNS-объекта само по себе не доказывает перехват всех DNS-запросов Android. Пользовательские ext: geodata требуют доступных файлов; встроены geoip.dat/geosite.dat закреплённого релиза.

## Проверки

Unit tests проверяют сохранность массивов правил и outbounds, DNS/balancers при runtime и Ping, выбор правильного ingress при первом DNS-inbound, запрет raw injector, missing balancer, отсутствие локальной подмены AUTO при refresh, duplicate identities. Import/refresh проходят статическую проверку до атомарной записи; при ошибке прежняя подписка сохраняется.

Настройки → «Проверить шаблон Remnawave / Xray JSON»: статический отчёт + проверка адаптированного JSON закреплённым ядром в отдельном процессе, даже при активном VPN. Большие JSON передаются через ParcelFileDescriptor pipe, без Binder payload overflow и без временных plaintext-файлов.

Ping XRAY проверяет полный профиль и его routing. OUTBOUND проверяет отдельный outbound, а не применимость всего шаблона; для AUTO всегда используется полный профиль. TCP/HTTPS — доступность endpoint, не подтверждение VPN. URL проверки соединения берётся из пользовательских настроек, поскольку шаблон может намеренно блокировать стандартный проверочный домен.

Проверка ядром не равна проверке маршрутизации. Контролируемый runtime-тест должен подтвердить разрешённый TCP/HTTPS, заблокированный домен/порт, DNS, UDP/IPv6, selectors/balancers и отсутствие изменений после Refresh. Фактические runtime результаты будут отражены в TEST_REPORT/ANDROID_TEST_MATRIX; конкретные production-шаблоны пока NOT RUN.

## XDrive / MASQUE

В проверенном upstream regular host generator отсутствуют отдельные builder types MASQUE/XDrive. Это не доказывает невозможность статического полного JSON или mapper, но не позволяет заявить штатный экспорт их из Host UI. Клиент сохраняет поддерживаемый полный Xray JSON без потери новых полей. Необходимый серверный контракт и границы проверки описываются отдельно; production-панель не менялась.
