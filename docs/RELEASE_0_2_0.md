# Signet Android 0.2.0

Version code 2, minSdk 26, targetSdk 36. ABI: arm64-v8a, armeabi-v7a, x86_64. Устанавливаемый release с R8/resource shrinking, подписан development key. Для телефона обычно нужен один arm64 APK. Сборка: strict Gradle tests/lint/Debug/Release; проверка: scripts/verify-apks.py; упаковка: scripts/package-release.py.

Проверенный код: функциональный APK 291e4ce, QA/workflow 93769e7. [CI 44288047516 SUCCESS](https://github.com/kirkanw88/signet-android/actions/runs/44288047516): strict verification на чистом Gradle cache, 42 core tests, Lint 0 errors/39 warnings, 17 Android tests и 7 release Home responsive checks. После этого меняются только отчёты/доказательства. Итоговые hashes локальных файлов — release/SHA256SUMS.txt.

Реализованы: delivered AUTO, группировка/flags, четыре режима проб с ограничениями, собственный QR, per-app JSON, Remnawave metadata/template review, сохранение sniffing, реалистичная Земля и однотонные страны, public GitHub updater, browser account/payment flows. Поддерживаемые full JSON XDrive/MASQUE не подменяются другим транспортом.

Частично: host transports подтверждены, Android runtime отдельно в CI; физическая матрица/утечки/soak не пройдены. XDrive real-provider OAuth не выполнялся. Hysteria2 conversion не выдаётся за e2e. Геометрия/GLES compile не доказывают физические FPS и все launcher masks.

BLOCKED: native account/session/billing API отсутствует; предложенный API_CONTRACTS.md не развёрнут на backend. Обновления из Public GitHub Releases требуют публичного выпуска: текущий private repository не открывался, PAT в APK не встраивается. Production signing не предоставлен.

Коммиты этапов: A 72263ae, B 8d9fa8b, E ba7cc34, F bf7803f, C ae3d88f, D 9963fb0; G — последующий release/security/tests commit в истории ветки. Ничего не объединялось с main. Проверенные результаты и реальные ограничения — TEST_REPORT.md, ANDROID_TEST_MATRIX.md, SECURITY_AUDIT_0_2_0.md.
