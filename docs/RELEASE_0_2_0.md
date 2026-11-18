# Signet Android 0.2.0

Version code 2, minSdk 26, targetSdk 36. ABI: arm64-v8a, armeabi-v7a, x86_64. Устанавливаемый release с R8/resource shrinking, подписан development key. Для телефона обычно нужен один arm64 APK. Сборка: strict Gradle tests/lint/Debug/Release; проверка: scripts/verify-apks.py; упаковка: scripts/package-release.py.

Реализованы: delivered AUTO, группировка/flags, четыре режима проб с ограничениями, собственный QR, per-app JSON, Remnawave metadata/template review, сохранение sniffing, реалистичная Земля и однотонные страны, public GitHub updater, browser account/payment flows. Поддерживаемые full JSON XDrive/MASQUE не подменяются другим транспортом.

Частично: host transports подтверждены, Android runtime отдельно в CI; физическая матрица/утечки/soak не пройдены. XDrive real-provider OAuth не выполнялся. Hysteria2 conversion не выдаётся за e2e. Геометрия/GLES compile не доказывают физические FPS и все launcher masks.

BLOCKED: native account/session/billing API отсутствует; предложенный API_CONTRACTS.md не развёрнут на backend. Обновления из Public GitHub Releases требуют публичного выпуска: текущий private repository не открывался, PAT в APK не встраивается. Production signing не предоставлен.

Коммиты этапов: A 72263ae, B 8d9fa8b, E ba7cc34, F bf7803f, C ae3d88f, D 9963fb0; G — последующий release/security/tests commit в истории ветки. Ничего не объединялось с main. Проверенные результаты и реальные ограничения — TEST_REPORT.md, ANDROID_TEST_MATRIX.md, SECURITY_AUDIT_0_2_0.md.
