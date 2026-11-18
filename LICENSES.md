# Лицензии и происхождение

Бренд Signet: исходное изображение предоставлено пользователем, сохранено в assets/branding/original.jpg. Не подразумевается передача прав на товарный знак. SVG и VectorDrawable — детерминированные производные исходного изображения.

libXray — MIT, XTLS, [исходный релиз](https://github.com/XTLS/libXray/tree/v26.11.9). Xray-core — MPL-2.0, [исходный релиз](https://github.com/XTLS/Xray-core/tree/v26.11.9); оригинальные тексты лицензий встроены в assets/licenses. Native AAR не изменялся. Соответствующие исходники и уведомления должны оставаться доступными при распространении.

AndroidX/Compose/Room/DataStore/WorkManager — Apache-2.0; Kotlin/coroutines — Apache-2.0; OkHttp/Moshi — Apache-2.0; Koin — Apache-2.0; ZXing — Apache-2.0, zxing-android-embedded — Apache-2.0. JTS — EPL-2.0 / EDL-1.0 dual license (для распространения может применяться EDL-1.0). JSON-java — public domain по upstream; проверяйте закреплённую версию. Полный граф Gradle зависимостей сохраняется отдельно в отчётах сборки; это не исчерпывающий юридический аудит всех транзитивных Go packages.

Natural Earth — public domain, https://www.naturalearthdata.com/about/terms-of-use/. NASA Blue Marble Earth/cloud photo texture distributed in the official three.js r176 examples; exact URL, credit and SHA are recorded in assets/sources.json and assets/photo-textures-source.json. Geographic data is not a statement about recognition of disputed borders.

Space background: AI-generated using the built-in imagegen tool for this project; not an astronomical observation. Original prompt and provenance: assets/space-source.json. No ESO image is included. Day/night geometry uses NOAA's general solar-position equations: https://www.gml.noaa.gov/grad/solcalc/solareqns.PDF.

Закрытые ресурсы/код Incy и Happ не включены. Production ключи подписи не включены. Статический REALITY private key в tests/local-vless.json является открытой тестовой fixture и не должен использоваться для публичного сервера.
# Локальные флаги 0.2.0

flag-icons, MIT, copyright Lipis и contributors. Исходный commit `ac7e0eb3259eb62aa6100c435607e2fe03896a97`, https://github.com/lipis/flag-icons. License включена в APK: assets/licenses/flag-icons-MIT.txt. PNG отрендерены из SVG этого проекта, без ресурсов Happ/Incy. Происхождение и контрольные суммы: assets/flags-source.json.

NASA Black Marble 2016 night lights: NASA Earth Observatory / Black Marble team, Suomi NPP VIIRS. Official source/credit/checksums: assets/night-source.json. Reduced locally to 2048×1024; NASA imagery terms https://www.nasa.gov/nasa-brand-center/images-and-media/. No NASA endorsement is implied.


Capital locations and English names: Natural Earth populated places, public domain. Offline selection and source checksum: assets/capitals-source.json; derived data: app/src/main/assets/capitals.json. Marker represents the geographic capital, not a measured server location.

Earth 8K detail day/night maps: Solar System Scope / INOVE, NASA-derived, https://www.solarsystemscope.com/textures/, CC BY 4.0 https://creativecommons.org/licenses/by/4.0/. Runtime crops and blends a selected region. Notice is bundled at app/src/main/assets/licenses/earth-detail-CC-BY-4.0.txt; exact sources/hashes: assets/earth-detail-source.json.
