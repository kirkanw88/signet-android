# Mobile API клиент 0.2.4

Backend source подготовлен в соседнем локальном `signet_cabinet`: app/mobile_api.py, app/mobile_tokens.py, scripts/migrations/mobile_api_v1.sql, tests/test_mobile_api.py. Review/deployment guide: docs/MOBILE_API_V1.md в кабинете. 27 backend SQL/contract/regression тестов прошли на локальном PostgreSQL. После отдельного разрешения пользователя API развёрнут 18.11.2026 19:55:59 UTC; внешние HTTPS capability и DB-backed guards проверены без доступа к клиентским данным. [Сводка bundle](../tests/evidence/0.2.4/backend-api.json).

Android контракт:

- База фиксирована: `https://signet-cab.com/api/mobile/v1`. Authorization code с S256/state через системный браузер, callback `com.signet.client://oauth/callback`. Вход Telegram для пользователя бота, Google/Яндекс для кабинетного аккаунта; обязательной Telegram-привязки нет.
- POST `/token`: public client_id signet-android, code_verifier/redirect_uri либо refresh. Access 15 минут, refresh family 30 дней, rotation/replay revocation. Запросы сериализованы Mutex; фонового refresh при закрытом приложении нет. Повторный POST при потере соединения отключён.
- GET `/account`: общий баланс, подписки и статус. PUT `/subscriptions/{id}/settings` — явный autopay_enabled; GET/PUT/DELETE собственных устройств, без raw HWID в клиенте.
- Поддержка: GET/POST `/support/messages`, GET private image; общий кабинетный store. Не более одной страницы 50 сообщений в памяти, фото до 5 MiB, одно увеличенное изображение максимум 1024 пикселя. Poll только пока экран открыт, 15 s; account refresh 30 s, foreground only.
- Отдельная Room vault строка с AES-GCM ciphertext и отдельным Android Keystore alias. Account JSON держится в RAM; credentials не пишутся в logs и не попадают в clipboard. HTTPS/TLS обязательны, API redirects запрещены, bearer не отправляется на чужие hosts.
- Ошибка 404/503 при недоступном новом backend отображается явно. Не создаются фиктивный баланс, неподтверждённое состояние входа или второй mobile user.

Пополнение, продление и изменение тарифа используют текущий web checkout с проверками цены и ownership. Native API v1 не принимает произвольные суммы или бесплатное увеличение лимита. Deployment выполнен; полный OAuth/клиентский end-to-end на физическом устройстве ещё не подтверждён.
