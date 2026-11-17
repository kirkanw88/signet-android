# Signet 0.1.0 — отчёт 17.11.2026

Xray/libXray v26.11.9, Kotlin/Compose, native Android VpnService TUN. Это устанавливаемая первая development release с локальной подписью разработки. Полная приёмка исходного ТЗ ещё не завершена.

## Исправление GitHub Actions / Dependency Verification

Исходный сбой подтверждён логом [Actions run 44171366263](https://github.com/kirkanw88/signet-android/actions/runs/44171366263), commit `143e04d41c3808916ea9193dcbe60bfaea820374`: `:app:javaPreCompileDebug`, конфигурация `:app:_agp_internal_javaPreCompileDebug_kaptClasspath`, артефакт `com.google.guava:guava-parent:33.2.1-jre`, репозиторий `MavenRepo`.

Причина: в `gradle/verification-metadata.xml` полностью отсутствовала запись `guava-parent-33.2.1-jre.pom`. Записи других версий Guava и checksum для JAR/module самой Guava не распространяются на parent POM. Это отсутствие checksum, а не несовпадение существующей суммы. `MavenRepo` соответствует `mavenCentral()` из settings.gradle.kts; признаков неожиданного репозитория в этом сбое нет. Репозитории остались google()/mavenCentral() и Gradle Plugin Portal для плагинов, с FAIL_ON_PROJECT_REPOS.

POM получен по HTTPS с `repo.maven.apache.org/maven2` и `repo1.maven.org/maven2`: 19431 байт, содержимое совпадает. Вычисленный SHA-256: `9095f6d8ee3765950207510785830cc2280e781067915fa99cfe1740d4865c14`. Дополнительно проверено совпадение с опубликованным Central SHA-1 `fc55955fab23e86fad230acc81bf570deb5660bd`. Sidecar `.sha256` для этого POM возвращает 404, поэтому SHA-256 вычислен из подтверждённых HTTPS-байтов, а не назван опубликованным Central SHA-256. Источники и значения: `tests/evidence/central-metadata.json`. Повторяемая проверка без изменения доверия: `python scripts/verify-central-metadata.py com.google.guava:guava-parent:33.2.1-jre guava-parent-33.2.1-jre.pom`.

Дополнительно найдена отсутствующая Linux-вариация закреплённого AAPT2 `com.android.tools.build:aapt2:8.10.1-12782657`, необходимая Ubuntu runner; ранее XML содержал только Windows JAR. Linux JAR получен с официального Google Maven, повторно через maven.google.com с переходом на dl.google.com; байты и опубликованный SHA-1 совпали. SHA-256 `52f864b7fd20a9ff09fc3db96162537a63c5b38ecc1c2549db4b491c6a517ff0`. Источники: `tests/evidence/google-metadata.json`. Это конкретный OS-артефакт, а не разрешение всем зависимостям Google Maven.

Добавлены только эти подтверждённые записи. `verify-metadata=true` сохранён, существующие суммы не заменены, trusted-artifacts / глобальные исключения не добавлены. Автоматическое дописывание checksum через `--write-verification-metadata` и отключение verification не применяются.

Workflow теперь явно запускает strict verification, `:core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`, использует отдельный `${{ runner.temp }}/signet-gradle` без восстановления старого кэша и сохраняет диагностические отчёты при сбое. Локальная проверка использует новый `.gradle/ci-verification-home`; старый dependency cache не копируется. Установленный Gradle 8.13 используется как инструмент, независимо от нового dependency cache. Сетевой прокси задан только в локальном окружении процесса, в репозиторий его настройки не включены.

Результаты нового полного прогона будут дополнены после завершения локальной сборки и Actions; исторические результаты ниже не заменяют этот прогон.

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
