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

## Проверка типовых шаблонов — 18 ноября 2026

По выбору пользователя проверены типовые конфигурации, без доступа к production-подпискам. Воспроизводимый стенд: `python scripts/transport-lab.py`; результаты: `tests/evidence/0.2.0/transport-host.json`. Официальный Xray 26.11.9 проверяется по SHA-256 архива. Все серверы и временные ключи находятся в игнорируемом локальном каталоге; серверы слушают loopback.

Дополнительно сверены реальные поля [DEFAULT_TEMPLATE_XRAY_JSON](https://github.com/remnawave/backend/blob/6853666801d6d1925b6aec55824106100485777a/src/modules/subscription-template/constants/default-templates.ts#L275). Fixture `core/src/test/resources/remnawave-default-rendered.json` воспроизводит контракт после добавления host outbound генератором: два исходных SOCKS/HTTP входа, sniffing с http/tls/quic и routeOnly=false, два DNS, правило BitTorrent → direct. Unit подтверждает сохранение этих полей и единственный TUN; host TCP/HTTPS/UDP DNS/TLS negative PASS. Передача BitTorrent и production-дефолты не тестировались. Поля стандартного upstream-шаблона не объявляются обязательными для любого шаблона панели.

PASS на host-стенде: готовый AUTO с двумя outbound и random balancer; split-routing с direct/proxy/block, GeoIP private и geosite google; разрешённый TCP, HTTPS с проверкой CA и имени, UDP DNS, DNS домена, запрещённый домен. Отдельно проверен отказ TLS при неверном имени. Это тест ядра на host, не доказательство Android TUN или production-совместимости.

Найден и исправлен дефект адаптера: исходный `sniffing` выбранного SOCKS/HTTP/TUN ingress теперь сохраняется в TUN и полном probe-профиле. Без доменного контекста правила `domain`/`geosite` могут не применяться к IP-пакетам Android. При отсутствии sniffing показывается `DOMAIN_SNIFFING`; клиент не включает его самовольно.

Подтверждена особенность Xray: `IPIfNonMatch` вместе с безусловным `network: tcp,udp` может завершить поиск до DNS/GeoIP для доменного запроса. Добавлено предупреждение `IPIFNONMATCH_CATCHALL`. Исходные domainStrategy и порядок правил не переписываются. В корректной GeoIP fixture используется IPOnDemand; это выбор автора тестового шаблона, а не изменение пользовательских конфигураций.

Системный Android DNS выбирается только среди глобальных числовых серверов. Domain-scoped DNS не становится резолвером для всех запросов. DoH-only, fakeDNS, несколько inbound и пользовательские `ext:` geodata требуют отдельной проверки политики; наличие JSON само по себе не доказывает отсутствие DNS-утечек.

В проверенном upstream regular host generator отсутствуют отдельные builder types MASQUE/XDrive. Это не доказывает невозможность статического полного JSON или mapper, но не позволяет заявить штатный экспорт их из Host UI. Клиент сохраняет поддерживаемый полный Xray JSON без потери новых полей. Необходимый серверный контракт и границы проверки описываются отдельно; production-панель не менялась.
