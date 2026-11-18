# Signet 0.2.0 — 18.11.2026

Ветка `feature/android-0.2.0`, база `fc4f885`. Только Android repository, production и main не менялись. Ниже этой секции сохранены исторические результаты 0.1.0.

| Проверка | Результат 0.2.0 |
|---|---|
| Strict Gradle Dependency Verification | PASS, не отключалась, метаданные доверия в 0.2.0 не расширялись |
| Core JVM | 42 tests, 0 failures/errors |
| app JVM | NO-SOURCE, не отдельный тест |
| Lint Debug | PASS, 0 errors; warnings в HTML/XML |
| Debug и Release/R8 | PASS, stage-G-routing-final.log, 2m 3s |
| Instrumentation APK | Strict compile PASS, 29s; это не runtime PASS |
| Release signature/ABI/identity | PASS, 0.2.0/code 2, все 3 ABI, Signature v2, zip/ELF 16 KB alignment |
| Точные GLES shaders | WebGL1 compile/link PASS; не Android FPS benchmark |
| Host transport e2e | 9/9 конфигураций PASS, transport-host.json |
| Android native TUN/UI 0.2.0 | Runtime job добавлен, результат обновляется после выполнения |
| Физические устройства, камера, мобильная сеть, soak | NOT RUN |

Чистый Ubuntu build job [44267311710](https://github.com/kirkanw88/signet-android/actions/runs/44267311710) для 280c5f4: SUCCESS, без restore Gradle cache; APK/report artifacts сохранены. В этом commit было 40 core tests; последующие default/locale tests дают 42 локально. Runtime того же run не исполнял тесты: emulator не нашёл AVD. Исправлен явный путь AVD, повтор выполняется отдельно. Предыдущая попытка 44265924197 остановилась на отсутствующем adb PATH. Ни один из этих инфраструктурных сбоев не выдаётся за тест приложения.

Текущий код 23a65fb также прошёл чистый Linux build job [44269709962](https://github.com/kirkanw88/signet-android/actions/runs/44269709962): 42 tests/0 failures/errors/skipped, Lint 0 errors/39 warnings, Debug/Release/R8 и APK checks SUCCESS. Проверены выгруженные XML/JSON artifacts, сводка — `tests/evidence/0.2.0/ci-build.json`. Runtime этого run загрузил emulator; его отдельный результат ожидается. После этого изменены только instrumentation/workflow/docs, устанавливаемое приложение не менялось.

Размеры APK: arm64-v8a 41,907,511 bytes; armeabi-v7a 41,183,517; x86_64 44,284,718. Устанавливаемые development releases с debug certificate; production key не задан. Хеши — `tests/evidence/0.2.0/apk-verification.json`.

По выбору пользователя проверены типовые Remnawave templates, без production-подписки. AUTO/balancer и split GeoIP/geosite прошли host TCP/HTTPS/UDP-DNS/domain-DNS/block проверки. Исправлено отсутствие переноса ingress sniffing в TUN/probe, добавлен unit-тест. IPIfNonMatch + catch-all диагностируется без изменения политики.

Официальный Xray 26.11.9: VLESS TCP, REALITY Vision, XHTTP REALITY, XDrive template, MASQUE h2/h3, AUTO и split. В каждом host случае подтверждены TCP, HTTPS с доверенной тестовой CA/правильным именем, UDP DNS и отказ неправильного TLS имени. Private CA/server keys остаются в игнорируемом `.gradle/transport-lab`. Реальный Google OAuth, S3/Yandex providers, публичный MASQUE, IPv6 egress и packet-loss/MTU: NOT RUN.

Проверка обновлений/баннер реализованы; публичный Release пока недоступен из-за private GitHub repository. Токен в APK не встроен, visibility не менялась. Native account/session/payment API отсутствует: browser кабинет реализован, native часть BLOCKED; контракт — API_CONTRACTS.md. Production не менялся, реальные платежи/входы не выполнялись.

Лимиты данных, ленивые списки и текстуры ограничивают память; нет traffic log, HTTP disk cache, probe history и auto APK download. Это не доказательство отсутствия утечек: финальный физический PSS/soak не получен. См. PERFORMANCE.md и ANDROID_TEST_MATRIX.md.

## Исторический отчёт Signet 0.1.0 — 17.11.2026

Xray/libXray v26.11.9, Kotlin/Compose, native Android VpnService TUN. Это устанавливаемая первая development release с локальной подписью разработки. Полная приёмка исходного ТЗ ещё не завершена.

## Исправление GitHub Actions / Dependency Verification

Исходный сбой подтверждён логом [Actions run 44171366263](https://github.com/kirkanw88/signet-android/actions/runs/44171366263), commit `143e04d41c3808916ea9193dcbe60bfaea820374`: `:app:javaPreCompileDebug`, конфигурация `:app:_agp_internal_javaPreCompileDebug_kaptClasspath`, артефакт `com.google.guava:guava-parent:33.2.1-jre`, репозиторий `MavenRepo`.

Причина: в `gradle/verification-metadata.xml` полностью отсутствовала запись `guava-parent-33.2.1-jre.pom`. Записи других версий Guava и checksum для JAR/module самой Guava не распространяются на parent POM. Это отсутствие checksum, а не несовпадение существующей суммы. `MavenRepo` соответствует `mavenCentral()` из settings.gradle.kts; признаков неожиданного репозитория в этом сбое нет. Репозитории остались google()/mavenCentral() и Gradle Plugin Portal для плагинов, с FAIL_ON_PROJECT_REPOS.

Путь зависимости подтверждён Gradle metadata/POM: `kapt(androidx.room:room-compiler:2.7.1)` → `androidx.room:room-compiler-processing:2.7.1` → `com.google.guava:guava:33.2.1-jre` → parent `com.google.guava:guava-parent:33.2.1-jre`. Локальный прогон с исходным XML и новым dependency cache воспроизвёл ошибку, HTML-отчёт явно указал `Checksums are missing from verification metadata`; извлечённая таблица сохранена в `tests/evidence/dependency-verification-before.txt`. Фактически скачанный Gradle POM совпал с проверенным Central SHA-256.

POM получен по HTTPS с `repo.maven.apache.org/maven2` и `repo1.maven.org/maven2`: 19431 байт, содержимое совпадает. Вычисленный SHA-256: `9095f6d8ee3765950207510785830cc2280e781067915fa99cfe1740d4865c14`. Дополнительно проверено совпадение с опубликованным Central SHA-1 `fc55955fab23e86fad230acc81bf570deb5660bd`. Sidecar `.sha256` для этого POM возвращает 404, поэтому SHA-256 вычислен из подтверждённых HTTPS-байтов, а не назван опубликованным Central SHA-256. Источники и значения: `tests/evidence/central-metadata.json`. Повторяемая проверка без изменения доверия: `python scripts/verify-central-metadata.py com.google.guava:guava-parent:33.2.1-jre guava-parent-33.2.1-jre.pom`.

Дополнительно найдена отсутствующая Linux-вариация закреплённого AAPT2 `com.android.tools.build:aapt2:8.10.1-12782657`, необходимая Ubuntu runner; ранее XML содержал только Windows JAR. Linux JAR получен с официального Google Maven, повторно через maven.google.com с переходом на dl.google.com; байты и опубликованный SHA-1 совпали. SHA-256 `52f864b7fd20a9ff09fc3db96162537a63c5b38ecc1c2549db4b491c6a517ff0`. Источники: `tests/evidence/google-metadata.json`. Это конкретный OS-артефакт, а не разрешение всем зависимостям Google Maven.

Добавлены только эти подтверждённые записи. `verify-metadata=true` сохранён, существующие суммы не заменены, trusted-artifacts / глобальные исключения не добавлены. Автоматическое дописывание checksum через `--write-verification-metadata` и отключение verification не применяются.

Workflow теперь явно запускает strict verification, `:core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`, использует отдельный `${{ github.workspace }}/.gradle/ci-user-home` без восстановления старого кэша и сохраняет диагностические отчёты при сбое. Локальная проверка использует новый `.gradle/ci-verification-home`; старый dependency cache не копируется. Установленный Gradle 8.13 используется как инструмент, независимо от нового dependency cache. Сетевой прокси задан только в локальном окружении процесса, в репозиторий его настройки не включены.

Проверка чистого окружения завершена 18.11.2026 (Europe/Moscow): [Actions run 44174008896 — SUCCESS](https://github.com/kirkanw88/signet-android/actions/runs/44174008896), commit `62e9190aa78a0f628aa06b42263541945805a385`. Gradle: `BUILD SUCCESSFUL in 8m 49s`. В логе подтверждён новый `GRADLE_USER_HOME=/home/runner/work/signet-android/signet-android/.gradle/ci-user-home`, без restore dependency cache.

| Проверка в новом Ubuntu 24.04 runner | Результат |
|---|---|
| Dependency Verification, strict | PASS, включая проблемную javaPreCompileDebug/KAPT configuration |
| `:core:test` | 10 tests, 0 failures/errors/skipped; XML: `tests/evidence/ci-dependency-unit-tests.xml` |
| `:app:testDebugUnitTest` | NO-SOURCE; дополнительных JVM-тестов в app нет |
| `:app:lintDebug` | PASS, 0 errors, 29 warnings |
| `:app:assembleDebug` | PASS, APK всех настроенных ABI созданы |
| `:app:assembleRelease` | PASS, R8 + resource shrinking, APK созданы |

[APK из этого прогона](https://github.com/kirkanw88/signet-android/actions/runs/44174008896/artifacts/14369252538) и [исходные отчёты тестов/Lint](https://github.com/kirkanw88/signet-android/actions/runs/44174008896/artifacts/14368519352) загружены Actions. Фильтрованный журнал шага сборки: `tests/evidence/ci-dependency-build.txt`. Все 1063 прежние записи артефактов сохранены; добавлены ровно две записи, описанные выше. Других отсутствующих checksum в полном Ubuntu-прогоне не выявлено.

Локальный полный прогон Windows завершился `BUILD SUCCESSFUL in 11m 30s`, 110 tasks (108 executed, 2 up-to-date). Использованы `clean`, `--no-build-cache`, отдельный новый dependency cache и те же strict-задачи. Начальный прогон для воспроизведения заполнил этот новый кэш; старый Gradle dependency cache не копировался. Unit: 10 tests, 0 failures/errors/skipped. Lint: 0 errors, 29 warnings. Debug и Release успешно собраны, в сумме 8 APK (три ABI + universal для каждого build type). Журнал задач: `tests/evidence/local-dependency-build.txt`, размеры/SHA-256 APK: `tests/evidence/local-dependency-apks.json`. ARM64 Release: `app/build/outputs/apk/release/app-arm64-v8a-release.apk`, 41052886 байт, SHA-256 `c71e6481f455a20571fefa5b2cdb4049a4b87f6b0d4164fed043fc244f546d3a`; apksigner verification и `zipalign -c -P 16 4` прошли. Подпись остаётся development/debug, как в исходной конфигурации. Установка/инструментальные проверки в этом исправлении CI не выполнялись.

Для старого установленного JDK 17.0.1 используется существующий ASCII junction `C:\Android\signet-build`, проверенно указывающий на этот же репозиторий; файлов проекта вне запрошенного репозитория не создавалось. Чистое разрешение зависимостей и сборка через wrapper на Ubuntu независимо подтверждены run выше.

Первый промежуточный push был отклонён валидатором Actions до создания job из-за `runner.temp` в job-level env. Это исправлено на разрешённый там `github.workspace`; успешный run выше включает исправление. Ошибок сборки после добавления двух подтверждённых checksum в Ubuntu не осталось. Исторические проверки приложения ниже не означают повторного запуска UI/инструментальных тестов в рамках этого исправления CI.

## Подтверждено

| Проверка | Результат | Доказательство |
|---|---|---|
| Release build, R8, ABI splits | PASS | `tests/evidence/build-final.txt` |
| Core unit tests | 10 PASS, 0 failures | `tests/evidence/unit-tests.xml` |
| Android Lint | 0 errors, 29 warnings | `tests/evidence/lint.txt` |
| Подпись ARM64 APK | PASS, APK Signature Scheme v2 | `tests/evidence/apk-signature.txt` |
| Выравнивание ZIP 16 KB | PASS | `zipalign -c -P 16 4` |
| Интеграционные тесты debug, предыдущий запуск | 5 PASS | Зафиксировано во время разработки; сырой общий вывод последующего запуска был перезаписан |
| UI tests, предыдущий запуск | 1 PASS / 1 FAIL | Исправлена синхронизация диалога; повторная проверка BLOCKED |
| Повтор полного набора и установка финального release | BLOCKED | Эмулятор после перезапуска не загружает Android / ADB offline |
| Матрица размеров и RAM финального release | BLOCKED | Та же неисправность стенда |

Предупреждения Lint включают закреплённые более ранние версии библиотек, размер и сложность векторной трассировки логотипа. Это не формальная проверка уязвимостей. Проверка ZIP-выравнивания не заменяет проверку ELF всех ABI на реальном 16 KB устройстве.

## Интеграционная среда

API 36 x86_64 emulator; локальный официальный Xray 26.11.9 слушает только host loopback. ADB reverse перенаправляет тестовые порты 10888–10890. Виртуальный путь 10.0.2.2 давал ненадёжные REALITY-соединения, поэтому для этих локальных тестов использован 127.0.0.1 через ADB. Это особенность стенда, не изменение пользовательских адресов в приложении.

Тесты проверяют преобразование VLESS REALITY, XHTTP TLS и Hysteria2 URI ядром; отсутствие тестового секрета в файлах Room; три полных подключения VLESS, REALITY/Vision и XHTTP REALITY. Для каждого сетевого сценария проверяются HTTP-проба до TUN, запуск native ядра, получение HTTPS через Android VPN, ненулевые счётчики и изолированная проба при активном VPN. DNS используется этими запросами, отдельный полный аудит утечек DNS не выполнен.

UI tests проверяют навигацию, восстановление Activity и отмену импорта. Предыдущая проверка отмены импорта завершилась assertion failure; ожидание появления диалога и проверка возвращения к подпискам исправлены в тесте, но успешный повтор не получен. Более ранний сырой вывод сохранён как `tests/evidence/ui-earlier.txt`. `tests/evidence/instrumentation-status.txt` фиксирует границы подтверждения. Финальную сборку нельзя объявлять прошедшей все семь тестов. Debug APK ранее устанавливался и работал; установка именно финального release после R8 пока не повторена.

## Статическая проверка

Полный JSON сохраняется, конфигурации шифруются Android Keystore AES-GCM, подписки ограничены HTTPS и размером, ссылки требуют подтверждения импорта. Нет production токенов и изменений инфраструктуры. Проверены перечни лицензий и источники native/assets. Формального пентеста и CVE-аудита не было. Подробности — SECURITY.md.

Логотип трассирован из пользовательского оригинала в прозрачные векторы. Границы — Natural Earth, геометрия Polygon/MultiPolygon с отверстиями. Сфера, освещение, атмосфера, облака и анимация реализованы GLES; снимки показывают внешний вид, но не доказывают все переходы анимации и точность всех полигонов мира.

## Ограничения / NOT RUN

- Физические ARM64/ARMv7 телефоны и Android 8–15: не проверены.
- Production Remnawave подписка, конкретный HWID-контракт и реальное продление: не проверены; пользовательская подписка не предоставлялась, production не затрагивался.
- Hysteria2: URI разбирается закреплённым ядром; end-to-end сервер не проверен.
- Произвольные balancers/routing, пользовательские DNS, IPv6 egress и широкая UDP-совместимость требуют отдельной матрицы. Наличие IPv6 TUN маршрутов не подтверждает весь IPv6 egress.
- Per-app include/exclude, reboot/Always-on/lockdown, переход Wi-Fi/mobile, длительный фон, батарея и длительные циклы reconnect: нет полной проверки на телефоне.
- AGE реализован с пользовательским ключом; закрытые криптоссылки конкурентов, их private APIs и отдельный менеджер custom geodata не реализованы.
- Нет полного паритета с Incy/Happ; сравнение публичных функций находится в FEATURE_PARITY.md.
- Производственный ключ подписи и публикация отсутствуют; перед публичным релизом обязательны собственный keystore и проверки на физических устройствах.

Память, ограничения накопления данных и пределы измерений: PERFORMANCE.md. Совместимость и размеры окон: DEVICE_MATRIX.md.
