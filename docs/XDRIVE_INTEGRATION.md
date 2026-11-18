# XDrive

Android TUN template backend: PASS в финальном API36 CI 44288047516 — native validate, реальный TCP/HTTPS/UDP DNS, проверка при работающем VPN и сохранённый config. Это Debug instrumentation, не реальный Google Drive OAuth и не Release VPN e2e.

Минимальная подтверждённая версия: официальный libXray 26.11.9, Xray commit `676eaf66f8f05d4d558ee7a74b4736623d3feeb9`. Историческая первая версия не устанавливалась. Используется закреплённый официальный AAR; собственных патчей транспорта нет.

Полный JSON сохраняется. Backend: google-drive, template, s3, local; проверяются VLESS Encryption и включённый Mux. Local требует общего хранилища, обычный адрес сервера его не создаёт. Google Drive требует согласованных ClientID, ClientSecret, RefreshToken. Перед импортом OAuth backend требуется отдельное согласие; приложение не получает реальные Google токены автоматически. Разрешение сохраняется только для подписки. Реальный OAuth не выполнялся.

Host loopback template backend с ограниченным WebDAV-подобным хранилищем: TCP, HTTPS, UDP DNS PASS. Это проверяет реализацию XDrive, но не Google Drive/S3/provider availability. Реальный провайдер, отзыв OAuth, квоты и мобильная сеть: NOT RUN. Android TUN проверяется отдельным instrumentation job; его статус фиксируется в TEST_REPORT.

Исходники: https://github.com/XTLS/Xray-core/tree/676eaf66f8f05d4d558ee7a74b4736623d3feeb9/transport/internet/xdrive
