# Signet Android 0.2.5

- Исправлена общая серверная авторизация Telegram/Google/Яндекс: страница согласия сохраняет canonical Origin при form POST, а CSP разрешает возврат в установленный Signet. Origin:null и чужие origins не принимаются; CSRF, signed cookie, state и PKCE сохранены. Серверный hotfix уже развёрнут и работает также с 0.2.4.
- Границы выбранной страны при подключении/переподключении и после подключения выделяются светлой центральной линией, голубой обводкой и заливкой 17% вместо 7%. Ширина формируется сдвигом в screen space, поскольку многие GLES2 GPU ограничивают glLineWidth одним пикселем. Дополнительных bitmap/blur buffers нет, цвета переиспользуются без per-frame allocations. Вращение/zoom не менялись.
- versionName 0.2.5, versionCode 7, прежний package и development certificate: обновление поверх подписанной тем же сертификатом 0.2.4.

Проверки: 10 серверных policy/payment-return tests локально и в isolated image; browser old/new regression для трёх providers; live provider-start configuration без входа в клиентские аккаунты; Debug/Release, Android Lint, GLSL compile/link и APK signature/16 KB alignment.

Browser regression заменяет ответы identity providers и code store локальными stub; реальные Signet callback routes, cookies/state и form POST выполняются в Chrome. Production Google/Yandex starts проверены, но пароль/MFA реального аккаунта и callback на физическом Android не проходились. Длительный emulator/CI не повторялся. Отчёт: [TEST_REPORT](TEST_REPORT.md).
