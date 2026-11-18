# Архитектура

Версия 0.2.0: AUTO — только исходный профиль панели, без локальной оценки или fallback. Runtime заменяет вход на TUN с исходными tag/sniffing и сохраняет DNS/routing/outbounds/balancers; probeRuntime переносит тот же ingress в аутентифицированный loopback HTTP proxy отдельного процесса. Неиспользуемый основной SOCKS listener удалён. Метрики пока loopback TCP и не являются UID-private.

Импорт/refresh сериализованы; encrypted vault записывается до публикации состояния. Ошибка открытия vault запрещает перезапись. Probes передают ограниченный JSON через ParcelFileDescriptor pipe, не через большой Binder Bundle и не через plaintext-файл. Probe runtime уничтожается после отвязки; результаты только в RAM.

Обновления приложения — отдельный bounded public GitHub release client с ETag, 24-часовым auto interval и явным переходом к загрузке. Метаданные не содержат VPN-секретов. Кабинет/платежи используют действующие browser flows, native session не симулируется.

`core` — JVM-модуль: модели, непрозрачный импорт полного JSON, метаданные, AUTO, географические преобразования и unit tests. `app` — Android Compose, ViewModel/StateFlow, Koin, Room, DataStore, WorkManager, native bridge, VPN и OpenGL.

Поток подключения: системное согласие → foreground service → сериализованный lifecycle mutex → защищённые native-сокеты и DNS resolver → Android TUN → fd в `env.xray.tun.fd` → libXray `runXray` → реальная HTTPS-проверка выбранного outbound → CONNECTED. После этого системные пакеты передаёт native TUN. Отдельный инструментальный тест проверяет HTTPS без локального SOCKS, то есть непосредственно через системный TUN.

Один Go runtime на процесс. Главный процесс владеет VPN; `:probe` — временными batch-пробами, максимум пять одновременно. Пробы сериализованы группами; отдельный процесс не вызывает testXray/pingBatch в работающем VPN runtime. Проверка сокетов в процессе проб использует protect для предотвращения вложенного туннеля. После отвязки probe process завершает себя.

Room хранит единственную AES-GCM запись со снимком подписок. Ключ AES не покидает Android Keystore. DataStore содержит только настройки, не пользовательские VPN-конфигурации и не AGE-ключи. Пробы изменяют StateFlow без записи на диск.

Настройки и выбранный узел переживают перезапуск. Состояние CONNECTED не сохраняется на диск и не восстанавливается из старого флага: оно возникает только после реального запуска сервиса. Activity не владеет сервисом, смена ориентации его не останавливает.

Сетевые ошибки показываются без сырого текста исключений с URL/ключами. GPU и UI живут в lifecycle Activity, VPN — в Service. Нет второй сетевой схемы через tun2socks.
