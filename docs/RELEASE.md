# Выпуск APK

Версия Signet 0.1.0, libXray/Xray v26.11.9. Gradle 8.13, AGP 8.10.1, Kotlin 2.1.21; JDK 17, compile/target SDK 36, min SDK 26. Версии зависимостей закреплены в Gradle, native archive проверяется по ожидаемому SHA-256. Wrapper checksum фиксируется в gradle-wrapper.properties.

`assembleRelease` включает R8 и resource shrinking. ABI APK содержат ровно одну архитектуру; universal создан для диагностики, но не рекомендуется при известной архитектуре из-за размера. Итоговые пользовательские файлы копируются в `release/` со своим SHA256SUMS.txt.

Текущая release-конфигурация подписывает локальным debug signing config, ключ живёт вне репозитория. Это installable development release, не production signing. Перед публикацией замените signingConfig на инъекцию защищённого keystore через CI и повторите проверки. Не теряйте ключ: Android потребует ту же подпись для обновления без удаления данных.

Установка: открыть ARM64 APK на телефоне → разрешить установку из файлового менеджера → установить → импортировать пользовательскую подписку → подтвердить VPN. Не устанавливать APK другой архитектуры. Android 7 и ниже не поддерживаются.

Для ручной проверки: import, HTTPS, DNS, UDP/IPv6, переход Wi-Fi/mobile, фон, Always-on, per-app, исключения DNS/routing, stop/restart, отзыв согласия, reboot, повышенный font scale. Не публиковать результат как полностью прошедший ТЗ, если соответствующие пункты TEST_REPORT остаются NOT RUN/BLOCKED.
