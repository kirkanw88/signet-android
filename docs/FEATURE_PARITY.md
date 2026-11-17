# Сравнение функций — 17.11.2026

Источники: [официальный Incy README](https://github.com/INCY-DEV/incy-platforms), [Incy docs](https://docs.incy.cc/), [Happ docs](https://www.happ.su/main/llms.txt), [Happ Hysteria2](https://www.happ.su/main/faq/hysteria2.md). Это публичные заявления разработчиков, не результаты собственного тестирования чужих приложений. Закрытый код не использовался. В Incy RELEASE.json version=3.9.9 расходится с именем/URL Android 4.0.1; однозначную версию установленного клиента не утверждаем. Google Play Happ по указанному URL в браузерном исследовании вернул 404; документация доступна.

| Функция | Incy (публично) | Happ (публично) | Signet 0.1.0 | API/библиотека |
|---|---|---|---|---|
| Xray, VLESS REALITY | Да | Да | Реальное ядро | libXray |
| Hysteria2 | Да | Документирована | Импорт/ядро; e2e отдельно | Core Hysteria |
| URL, QR, share import | Да | Документировано | Реализовано | OkHttp, ZXing |
| JSON, routing, balancers | Да | Routing документирован | Полный JSON сохраняется | Xray JSON |
| Per-app Android | Да | Точный набор не проверен | Include/exclude | VpnService.Builder |
| HTTP-пробы | Реализация не проверена | Ping документирован | Отдельный runtime, реальные ms | pingBatch |
| HWID | Документирован | Документирован | Опциональный ID установки | HTTPS headers, Keystore |
| Зашифрованные подписки | Есть закрытые/provider форматы | Документирован свой формат | AGE; не закрытые схемы конкурентов | libXray |
| Работа в фоне | Не проверена лично | Не проверена лично | Foreground VPN | Android Service |
| Kill switch | Публично заявлен | Не подтверждён здесь | Системный Always-on/Block | Android settings |
| 3D Земля и границы | Не подтверждено | Не подтверждено | Реализованы | GLES2, Natural Earth, JTS |

Нет заявления о полном функциональном паритете с закрытыми клиентами. Приватные API, биллинг, TV-pairing, виджеты и их закрытые криптоссылки не входят в эту сборку.
