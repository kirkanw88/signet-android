# UI/UX 0.2.0

Финальный API36 runtime: 6/6 UI/QR/graphics tests PASS; manual update, banner show/dismiss, import open/cancel при временном уведомлении, navigation/recreate. Release Home responsive 7/7 PASS, включая landscape и font 1.3. RU/DE/FI/JP/TR кадры после actual GL readiness просмотрены; uniform fill и контрастный marker видны. Эти кадры DISCONNECTED и не доказывают connected zoom/FPS. Свежий AVD 4 GB завершил полный прогон без System UI ANR; физические устройства не проверены.

Главная: компактная 3D Земля, вертикальный LazyColumn локаций и доступная кнопка соединения; landscape — две колонки. Страны сворачиваются, есть поиск, избранное, сортировка, Ping/Refresh. Флаги — локальные assets; название отделено от description/transport/probe state. AUTO имеет отдельный значок и появляется только из подписки. Opaque ID/source остаются неизменными.

Параметры проб, приложения, шаблон и аккаунт — полноэкранные панели с прокруткой. Per-app: поиск name/package, системные приложения, иконки видимых строк, три режима, select all/clear, JSON preview с отсутствующими пакетами. Видимость ограничена Android profile/PackageManager, QUERY_ALL_PACKAGES не добавлен.

QR scanner собственный: runtime permission до открытия камеры, lifecycle pause/resume, torch, error/retry, debounce и предварительный импорт. QR image bounded/downsampled. Импорт не помещает секреты в SavedState и очищает поля при отмене/подтверждении. Настройки содержат ручную проверку обновлений; баннер предлагает скачать или отложить, без автоматической установки.

Глобус: однотонная непрозрачная заливка без country glow/pulse, densified spherical geometry, Polygon/MultiPolygon/holes, zoom по размеру страны; ночная карта NASA, облака/атмосфера/звёзды. Adaptive icon содержит знак и SIGNET внутри safe area. Shader compile/link PASS. Точность границ, clipping и FPS на реальных GPU не объявляются подтверждёнными по компиляции.

Автоматизированная responsive матрица описана в ANDROID_TEST_MATRIX.md. До реальных скриншотов нельзя объявлять все размеры/шрифты принятыми. Камера/launcher masks/доступность TalkBack на физическом устройстве: NOT RUN.
