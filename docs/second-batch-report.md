# Вторая порция: Prompt, фотография и демонстрационный результат

Дата: 5 октября 2026. Основа — коммит `d452509`; сохраняются Android Views / XML, Fragment, ViewBinding и один модуль `app`.

## Реализовано: A10–A14 / P3

- Prompt внутри вкладок AI Video / AI Photo: ввод, Copy, Clear, счётчик 0–300, отдельные сохраняемые черновики. Пробельный текст и 301+ символ блокируют Generate; вставка не обрезается молча. Для счётчика используется количество Unicode code points, поэтому обычный emoji учитывается один раз, составные emoji могут занимать несколько единиц.
- Video: переключение 720 / 1080. Photo: No style / Ghibli / 3D Person / Simpsons / Fantasy с иллюстрациями из PDF. Строка Name без конкретного стиля в эту порцию не добавлена.
- Instruction с Good / Bad choice и исходными фото, показываемая перед первым выбором. Continue сохраняет факт показа. Source sheet: системная галерея, камера, два локальных примера, отмена.
- Галерея использует Android GetContent с MIME image/*. Камера использует TakePicture и FileProvider. Внешние фото копируются в приватное хранилище, уменьшаются до 1600 px по длинной стороне, учитывается EXIF-ориентация. Временный доступ к исходному URI не нужен для последующего просмотра. Файлы размером больше 32 MiB и неподдерживаемые изображения переходят в состояние ошибки.
- Фото: Loading / Ready / Failed, Retry, Replace / Remove. Отмена sheet / системного выбора сохраняет прежний снимок. Неуспешная замена не перезаписывает предыдущее фото; до Retry / Remove создание заблокировано. Прерванная загрузка после восстановления ViewModel переходит в Failed.
- Use this effect открывает подготовку входного фото; Generate доступна после его загрузки. Это отдельный подтверждающий шаг перед списанием demo-токенов.
- Создание — сохраняемая задача с устойчивым ID и снимком входных данных. Повторный вызов для выполняющейся задачи возвращает тот же ID без второго списания. Выполнение продолжается независимо от открытого экрана; после пересоздания сессии задача завершается по сохранённому сроку.
- Creating для Video / Photo, уход через Okay в нужную вкладку Library, переход к Result после готовности, Failed с возвратом токенов и Retry. Возврат выполняется ровно один раз; повтор создаёт новую задачу и списывает стоимость один раз.
- Базовый Result: фото или соответствующий выбранному эффекту локальный MP4, looping, Pause / Play и сохранение паузы / позиции при пересоздании экрана. Пометка Demo result отделяет эти материалы от реальной генерации.
- Минимальный список задач в Library позволяет вернуться к Running / Failed / Ready. Полная сетка, удаление и действия над результатом остаются P4.
- Debug: долгий тап по балансу позволяет воспроизвести ошибку следующей загрузки фото или следующего создания, а также сменить аккаунт / сбросить demo-данные.
- IME insets: поле остаётся доступным с Android-клавиатурой; нижняя навигация скрывается во время набора. Формы и instruction прокручиваются на компактных экранах.

## Временные правила и неоднозначности PDF

Опорные страницы: Prompt 48 / 52 / 59 / 157 / 180, Instruction 64 / 119 / 209, Creating 97 / 186, Result 99 / 188. Полный источник — PDF из Downloads на 340 страниц. Дополнительная карта assets и контрольные суммы — `second-batch-assets.json`.

1. Стоимость Prompt в разных макетах — 10 / 30 без полного правила. В демо Video 720 стоит 10, Video 1080 — 30, Photo Prompt — 10 независимо от стиля. Стоимость эффекта пока прежняя: Video 40 / Photo 20. Правила находятся в `DemoSession.cost`, общие пределы — `DemoRules`. PRO и баланс независимы; полное покрытие entitlement / offers относится к P5.
2. Начальный баланс сохранён: 5. При недостатке можно нажать **Add demo tokens**: добавляются 100 локальных токенов и продолжается первоначальный запрос. Денег, магазина и сервера здесь нет. Это временный переход для обзора полного сценария; тарифы / покупки появятся в P5.
3. Создание завершается примерно за 4 секунды. Текст Photo исправлен на photo; в PDF встречается повтор описания video. Ошибка возвращает всю стоимость, что является временной demo-политикой.
4. Реальная генерация по prompt / загруженному фото не выполняется. Result выбирается из fixtures. Для Photo стиль определяет образец; для эффекта используется его медиа. Запрос и фото сохраняются для сценариев будущей интеграции.
5. PDF не содержит оригинальных видеороликов. Подготовлены 9 коротких локальных MP4 из исходных кадров: мягкое увеличение, 3 секунды, 24 fps, 480×640, без звука. Пропорции сохраняются через crop по центру. Значения 720 / 1080 относятся к запросу демо, а не к фактическому разрешению этих тестовых файлов.
6. В макетах встречается выбор до четырёх фото, но число обязательных снимков и зависимость от эффекта не определены. Эта порция поддерживает **одну reference photo**: обязательную для Effect и необязательную для Prompt. Расширение до нескольких — отдельная задача после уточнения правила.
7. Creating ribbon — статичная иллюстрация из PDF; исходная motion-анимация отсутствует. Показ процесса дополняется Android progress bar.
8. Share реагирует пояснением о следующем demo-обновлении. Меню Result пока содержит Creation details и Share. Реальные Save / Files / Share / Delete запланированы в P4.
9. Галерея и камера — интерфейсы Android, их iOS-макеты не воспроизводятся. GetContent не требует доступа ко всей библиотеке. Если URI недоступен после прерванного импорта, нужно выбрать файл снова.
10. Используется прежний Android font fallback вместо отсутствующего файла SF Pro. Числовые цветовые токены и общие типографические роли не изменялись. Для Generate disabled выбран существующий label/quaternary, чтобы подпись была читаемой.

## Проверено

- `assembleDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest`, `lintDebug`: успешные выполнения.
- 12 unit tests: 7 новых, 4 проверки первой порции, 1 шаблонный тест. Покрыты границы 1 / 299 / 300 / 301, Unicode / пробелы, обязательность фото, однократное списание, недостаток баланса, восстановление задач, однократный refund / retry и независимость черновиков.
- 9 instrumentation tests на Pixel 7 / Android 14 (API 34): 4 сценария P3, тест сериализации Preferences, 3 сценария каталогов / навигации и 1 шаблонный тест. Проверены обе ветки до Result, отмена source sheet, ошибка фото / Retry, ошибка создания / refund / Retry, выход в Library, восстановление ввода и видеопаузы при пересоздании Activity, реальный импорт file URI с сохранением после нового запуска, round trip запросов / задач через JSON / Preferences.
- Lint: 0 ошибок, 97 предупреждений — 88 неиспользуемых ресурсов дизайн-системы / заготовок, 9 уведомлений о версиях зависимостей и инструментария.
- Визуально проверены Prompt обоих типов, инструкция, выбор источника фото, Creating, Result с фото / воспроизведением видео и клавиатура. Снимки финальной APK и результаты находятся в папке outputs этой задачи.
- `git diff --check` без ошибок. Новый коммит не создавался.

Границы проверки: реальная съёмка камеры и выбор файла во внешнем приложении галереи, процессное восстановление во время внешнего picker, API 24 / 37, landscape / увеличенный системный шрифт отдельно не прогонялись. Сохранение задач после нового процесса основано на том же проверенном механизме JSON / Preferences и сроке задачи; принудительный process kill проверяется отдельно при полном сценарном аудите.

## Как пройти демо

1. AI Video / AI Photo → Prompt → ввести идею → выбрать параметры → Generate → Add demo tokens при недостатке → Result.
2. Карточка эффекта → Use this effect → Instruction → Continue → Use a sample photo → выбрать портрет → Generate → Result.
3. На Creating нажать Okay: задача остаётся в соответствующей вкладке Library. Нажать строку создания, чтобы вернуться к процессу / результату.
4. Для ошибки: долгий тап по балансу → Fail next photo upload / Fail next creation → повторить обычный маршрут. Ошибка создания возвращает стоимость; Retry создаёт новую попытку.

## Изменённые файлы

Полный список файлов этой порции приведён ниже.

- `app/build.gradle.kts`
- `app/src/androidTest/java/com/rslnabk/aivideotest/GenerationFlowTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/GenerationPersistenceTest.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/rslnabk/aivideotest/AppViewModel.kt`
- `app/src/main/java/com/rslnabk/aivideotest/MainActivity.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/demo/DemoResultFixtures.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/demo/DemoSession.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/demo/GenerationJson.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/demo/PhotoImporter.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/demo/PreferencesDemoStore.kt`
- `app/src/main/java/com/rslnabk/aivideotest/model/Catalog.kt`
- `app/src/main/java/com/rslnabk/aivideotest/model/Generation.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/catalog/BrowserFragment.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/common/DemoDialogs.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/effect/EffectFragment.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/generator/CreatingFragment.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/generator/GeneratorFragment.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/generator/PhotoDialogs.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/generator/PromptEditor.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/result/ResultFragment.kt`
- `app/src/main/res/color/ds_generate_content.xml`
- `app/src/main/res/drawable-nodpi/demo_bad_1.webp`
- `app/src/main/res/drawable-nodpi/demo_bad_2.webp`
- `app/src/main/res/drawable-nodpi/demo_creating.webp`
- `app/src/main/res/drawable-nodpi/demo_good_1.webp`
- `app/src/main/res/drawable-nodpi/demo_good_2.webp`
- `app/src/main/res/drawable-nodpi/demo_style_3d.webp`
- `app/src/main/res/drawable-nodpi/demo_style_fantasy.webp`
- `app/src/main/res/drawable-nodpi/demo_style_ghibli.webp`
- `app/src/main/res/drawable-nodpi/demo_style_simpsons.webp`
- `app/src/main/res/layout/fragment_creating.xml`
- `app/src/main/res/layout/fragment_generator.xml`
- `app/src/main/res/layout/fragment_result.xml`
- `app/src/main/res/layout/view_flow_header.xml`
- `app/src/main/res/layout/view_prompt_editor.xml`
- `app/src/main/res/raw/demo_video.mp4`
- `app/src/main/res/raw/demo_video_anime_1.mp4`
- `app/src/main/res/raw/demo_video_anime_portrait.mp4`
- `app/src/main/res/raw/demo_video_fashion.mp4`
- `app/src/main/res/raw/demo_video_gold.mp4`
- `app/src/main/res/raw/demo_video_japan.mp4`
- `app/src/main/res/raw/demo_video_penguin.mp4`
- `app/src/main/res/raw/demo_video_portrait.mp4`
- `app/src/main/res/raw/demo_video_underwater.mp4`
- `app/src/main/res/values/photo_sheet_styles.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/xml/file_paths.xml`
- `app/src/test/java/com/rslnabk/aivideotest/GenerationSessionTest.kt`
- `docs/screen-implementation-plan.md`
- `docs/second-batch-assets.json`
- `docs/second-batch-report.md`
- `gradle/libs.versions.toml`
- `tools/extract_generation_assets.py`
- `tools/generate_demo_clip.swift`
