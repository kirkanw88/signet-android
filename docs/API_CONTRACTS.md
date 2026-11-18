# API-контракты Signet Android 0.2.0

## Подтверждённые действующие поверхности

18.11.2026 выполнен только GET публичного `https://signet-cab.com/openapi.json` и главной страницы. Не выполнялись вход, регистрация, checkout, payment check, изменения серверов или операции с чужим аккаунтом. OpenAPI содержит GET /login, /auth/{provider}, /auth/telegram, /cabinet, /devices/{sub_id}, /payment/order/{order_id}, /support; POST /telegram-app/auth, /auth/google/token, /auth/yandex/token, /checkout и /payment/check. Для ряда POST request/response schema не описана. Эти маршруты не являются документированным native bearer API.

Android использует реальные браузерные `/auth/google`, `/auth/yandex`, `/cabinet`, `/support`, `/terms`, `/privacy` и официальный https://t.me/signet_it_bot. Cookies/SSO и банковские данные остаются в браузере/сервере. Не создаётся второй аккаунт, не извлекаются cookies, Telegram user ID не принимается как доказательство входа. Native экран не утверждает, что пользователь вошёл в APK или что платёж подтверждён. После входа подписка импортируется из кабинета. Native session refresh/связка аккаунта/заказы — BLOCKED до подтверждённого API; это не реализованные серверные функции.

## Предлагаемый контракт native API — НЕ РАЗВЁРНУТ / НЕ ВЫЗЫВАЕТСЯ

Нужна отдельная согласованная серверная задача. Пример пути `/api/mobile/v1` ниже — спецификация предложения, не обнаруженный endpoint.

- GET auth/authorize: state + PKCE S256 challenge, redirect только в подтверждённый Android App Link. Сервер повторно использует действующие OAuth/Telegram identity; Telegram initData проверяется сервером, не заменяется user ID.
- POST auth/exchange: одноразовый code, PKCE verifier, state binding; короткий access token, вращаемый refresh token, expiry/subject/scopes. Сервер проверяет redirect и PKCE; codes имеют TTL и single use. Native токены — только Android Keystore encrypted vault; их нельзя возвращать через URL.
- POST auth/refresh / auth/revoke: refresh rotation/reuse detection/logout; явные 401 и retry limits. Не использовать web OAuth client secrets в APK.
- GET me, subscriptions, subscriptions/{id}/devices: ownership enforced server-side; tariff/status/expiry/traffic/device limit, доступные действия. JSON не содержит admin Remnawave token.
- POST orders: Idempotency-Key, тариф/операция/целевой subscription ID; сервер пересчитывает цену и право операции, возвращает order ID/pending/allowlisted provider redirect. APK не передаёт сумму как авторитетную.
- GET orders/{id}: серверный итог pending/paid/cancelled/failed/refunded, принадлежность заказа и параметры покупки; success browser return не равен paid.
- POST orders/{id}/cancel: только допустимые pending, idempotent. Повторные webhook/запросы не зачисляют средства дважды.

Для App Links нужен контролируемый домен и assetlinks.json с production signing certificate. Случайный debug certificate не должен получать production trust. Native UI/клиент API подключаются только после сверки реальной опубликованной схемы и sandbox-тестов. Play Billing/внешние способы оплаты зависят от страны/канала; текущая APK-сборка для прямой установки не объявляется готовой к Google Play publication.

## Контракт профилей

Полный JSON из Remnawave передаётся как объект/массив с outbounds, DNS, routing, balancers. AUTO доставляется отдельным элементом: точное remarks AUTO либо Signet extension signet.selectionMode=auto. signet.profileId — необязательный стабильный идентификатор между обновлениями; signet.tunInboundTag — явный tag для Android ingress. Эти поля документируют контракт клиента; не заявляются универсальными полями Remnawave или уже настроенными в production. Без AUTO элемент не создаётся. Подробности: REMNAWAVE_TEMPLATE_COMPATIBILITY.md.

Официальные Remnawave headers: profile-title, subscription-userinfo, profile-update-interval, profile-web-page-url, announce, support-url, subscription-refill-date. Отсутствующий device limit не придумывается; точные лимиты/устройства требуют защищённого account API либо браузерного кабинета. HWID — обязательный стабильный installation UUID, без обхода ограничений; отправляется только на исходный HTTPS host подписки. Опции отключения в UI нет.

## Публичные обновления APK

Выбран пользователем GitHub Releases. GET `https://api.github.com/repos/kirkanw88/signet-android/releases/latest`, без Authorization/HWID/cookies, stable published releases only. SemVer сравнивается численно, артефакт выбирается локально по supported ABI, допускаются только HTTPS URL этого repository на github.com. Нет автоматического скачивания/установки или понижения версии.

Автопроверка только при открытии, не чаще 24 часов; ETag/304, компактный единственный cache, ручная проверка с защитой от повторного запроса в течение минуты. В настройках можно отключить автоматическую проверку. Доступная новая версия отображается баннером; «Позже» запоминается для конкретной версии. Сеть/404/private repository показывают понятную ошибку при ручной проверке, не фиктивное обновление.

18.11.2026 репозиторий сделан публичным по прямому запросу владельца; опубликован v0.2.2, анонимный endpoint releases/latest проверен. В APK нет приватных GitHub credentials. Каждый новый выпуск и его проверка отражены в TEST_REPORT.

APK следующего выпуска должен иметь тот же package и сертификат подписи, что установленный APK. Случайный debug keystore нового GitHub runner не обеспечивает обновление поверх локальной development-сборки. До стабильного канала нужен постоянный signing key вне репозитория; смена ключа может потребовать переустановку с удалением данных. Приложение не обходит проверку подписи Android.

## Явный запрос JSON и постоянные ссылки — 0.2.3

Remnawave GET /:shortUuid/:clientType, clientType=json (также v2ray-json), явно выбирает XRAY_JSON. Accept: application/json недостаточен: Signet не входит в UA fallback Happ/INCY. Клиент запрашивает /json, сохраняет raw query/token и первоначальную ссылку для обновления/экспорта; на 404/405 однократно пробует исходный URL. 401/403 не обходятся; формат показан по реально полученному документу. Существующие /json, /v2ray-json и .json не дополняются. Источник: [Remnawave backend](https://github.com/remnawave/backend/tree/6853666801d6d1925b6aec55824106100485777a/src/modules/subscription).

Кабинет закреплён как https://signet-cab.com; старое сохранённое значение игнорируется. Иконка техподдержки открывает https://t.me/cyber_goos — контакт публичной страницы входа кабинета, проверенный 18.11.2026. Это браузерный/Telegram переход, без административного API. Трафик и срок берутся только из subscription-userinfo Remnawave; при отсутствии значений лимиты не придумываются.

Буфер обмена читается только по нажатию. Ссылка подписки вставляется в диалог подтверждения, список приложений — в preview схемы AppRouting v1. Копируемые данные помечены sensitive, в журнал не попадают; ограничения 16 KiB для URL и 256 KiB для списка приложений.
