# MASQUE

Подтверждён официальный Xray/libXray 26.11.9. Используется CONNECT-IP (RFC 9484), HTTP/2 или HTTP/3, `network=masque`, `security=tls`, без Xray Mux. Это не утверждение о поддержке CONNECT-UDP. Конфигурация не заменяется TCP/XHTTP и не переключается на другой транспорт при ошибке.

Loopback fixture проверяет оба ALPN: h2 и h3. TCP, HTTPS с корректной CA/именем, UDP DNS: PASS на host. Проверка неверного имени TLS: ожидаемый отказ. TLS-проверка не отключалась; тестовая CA ограничена тестовым клиентом. Серверные ключи не попадают в APK или репозиторий.

Android instrumentation проверяет оба транспорта через настоящий VpnService; фактический статус — TEST_REPORT. Публичный MASQUE endpoint, физические мобильные сети, IPv6 egress и MTU under loss: NOT RUN. Регулярный host generator проверенного Remnawave не имеет отдельного MASQUE builder; нужен готовый поддерживаемый Xray JSON.

Официальная документация: https://xtls.github.io/en/config/outbounds/masque.html
