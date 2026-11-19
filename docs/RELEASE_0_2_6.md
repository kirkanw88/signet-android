# Signet Android 0.2.6 — две редакции

Обе редакции называются **Signet VPN**, сохраняют логотип, интерфейс, планету, VPN-ядро, импорт ссылок/QR/JSON, список серверов, Ping, маршрутизацию приложений и журнал.

| Редакция | Пакет | APK |
|---|---|---|
| Signet | `com.signet.client` | `signet-0.2.6-<ABI>.apk` |
| Для сторонних подписок | `com.signet.client.external` | `signet-external-0.2.6-<ABI>.apk` |

В Signet остаются кабинет, авторизация, баланс, устройства, автопродление и встроенная техподдержка. Во второй редакции эти пункты и обращения к API кабинета отключены; аккаунт кабинета не создаётся. Ссылка кабинета не сохраняется. Нет OAuth intent filter Signet. Сторонняя HTTPS-ссылка запрашивается как введена, без автоматического добавления `/json`; JSON по прямой ссылке и обычные форматы подписок поддерживаются прежним парсером.

Пакеты можно установить рядом; Android разделяет их данные, ключи, WorkManager и установочные сессии. Общие VLESS/Hysteria2/SEND intent могут вызвать стандартный выбор приложения. Собственные ссылки импорта разделены: `signet://import` и `signet-external://import`. Системное ограничение одного активного VPN не меняется.

Обновления используют прежний публичный GitHub Releases, но выбирают APK своей редакции и ABI; кеш чужой редакции отклоняется. `external` намеренно не назван `universal`: старый updater трактует это слово как универсальный ABI. При публикации обычные Signet APK загружаются первыми для совместимости с updater 0.2.5.

Сборка: `./gradlew --dependency-verification strict :core:test :app:testSignetDebugUnitTest :app:testExternalDebugUnitTest :app:lintSignetDebug :app:lintExternalDebug :app:assembleDebug :app:assembleRelease`. Упаковка: `python scripts/verify-apks.py`, затем `python scripts/package-release.py`. Новые зависимости не добавлены, Dependency Verification сохранена.

Backend, production и main не изменяются. Проверки и ограничения: [TEST_REPORT](TEST_REPORT.md).
