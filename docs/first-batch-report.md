# Первая порция: каталоги и просмотр эффекта

Дата: 5 октября 2026. Android Views / XML, один модуль `app`.

## Что реализовано

- Точка запуска `MainActivity`, отдельная тема без ActionBar на базе существующей дизайн-системы, Android status/navigation bar insets.
- Пять вкладок: AI Video, AI Photo, Favorites, Library, Settings. Вкладки сохраняют свои экраны; вложенные Category / Effect скрывают нижнюю навигацию. Back возвращает к исходной категории и положению списка. На корневой вкладке Back сначала возвращает в AI Video.
- Два Trends-каталога на общих компонентах: баннер, пять секций, горизонтальные ряды карточек, See all. Video и Photo имеют разные ID и порядок Popular.
- Категории Popular / Anime / Fashion / New / Retro, сетка из двух колонок, открытие соответствующего эффекта.
- Просмотр эффекта, Like / Unlike, общий баланс. Лайки одновременно обновляют каталог, категорию, эффект и Favorites; сохраняются после перезапуска. Избранное разделено на Photos / Videos.
- Library показывает пустое состояние с переходом в каталог. Settings содержит начальную карточку подписки. Полное наполнение этих разделов относится к следующим порциям.
- Общее локальное состояние: избранное, баланс, статус PRO. В debug-сборке удержание баланса открывает сценарии Free / PRO / zero balance и сброс с подтверждением. Это отдельный debug-инструмент.
- 22 эффекта с устойчивыми ID, пять категорий каждого медиатипа, 10 уникальных изображений WebP из PDF. Повторяющиеся изображения Anime хранятся один раз. Медиа занимают около 1 MiB.

## Граница порции

Prompt, Use this effect, баланс и More реагируют пояснением о доступности следующих шагов в будущей demo-версии. Фото, создание результата, покупки, экспорт и полный Settings ещё не реализованы. Здесь нет списания токенов или имитации готового результата. При открытии Prompt можно вернуться к Trends.

A04–A08 реализованы в пределах этой порции; A09 реализован для сетки / Effect / Like, без Scroll Down guide. A01–A03 подготовлены для каталогов: остальные assets, тарифы и задачи будут добавляться вместе с соответствующими сценариями. A06 содержит общие данные и reset; skip onboarding появится при реализации онбординга.

## Источники и неоднозначности

Источник экранов — `157 - Higgsfield AI (Vlad, 06.08.2025) (Copy) (Copy).pdf`, 340 страниц. Опорные страницы: 43–44 / 152 для каталогов, 117 / 207 для категории, 118 / 208 для Effect, 240–244 для Favorites / Library, 326 для базовой карточки Settings. Токены и типографика берутся из уже интегрированной дизайн-системы.

1. PDF содержит изображения, но не исходные видео. Видеоэффекты открываются как **Still preview**; локальные тестовые клипы и видеоплеер отложены до сценария создания / результата. Motion не воспроизводится как будто он присутствует в PDF.
2. Названия карточек в PDF — `Name`. Golden Hour, Beach Day, Kyoto и другие названия — демонстрационные, записаны в string resources. При получении реального списка их можно заменить без изменения навигации.
3. Экспорт не сохраняет полный граф прототипа. See all открывает выбранную категорию, баннер — первый Anime-эффект текущего типа, карточка — свой эффект. Эти переходы являются согласованной реконструкцией плана.
4. В образцах баланс меняется между 5 / 2 / PRO. Используется единый сохраняемый аккаунт с исходным балансом 5; экраны не подставляют независимые цифры. PRO и количество токенов независимы.
5. Временная стоимость: Video 40, Photo 20; лимит Prompt 300. Эти правила вынесены в `DemoRules`, пока не используются для списания. Реальная стоимость и политика при ошибке остаются открытыми.
6. SF Pro не приложен как лицензированный Android-шрифт. Сохраняется ранее документированный Android sans-serif fallback и исходные размеры / веса / line heights.
7. Иконки — Android vector drawables, близкие по смыслу макету; iOS статус-бар, home indicator и системные экраны не копируются. Баннера в PDF несколько вариантов: взято законченное составное изображение из страницы 43, остальные карточки используют извлечённые исходные растровые изображения.
8. Scroll Down в PDF связан с дополнительным содержимым Effect. В этой порции один кадр каждого эффекта, поэтому эта подсказка не выводится до появления листания примеров.

Постраничные источники и контрольные суммы медиа записаны в `docs/demo-assets.json`; повторяемое извлечение — `tools/extract_demo_assets.py <путь-к-PDF>`. Из PDF извлекаются только материалы оформления; его тексты не исполняются как инструкции.

## Изменённые / добавленные файлы этой порции

Изменены:

- `app/build.gradle.kts` — ViewBinding, BuildConfig и необходимые Fragment / Lifecycle зависимости.
- `gradle/libs.versions.toml` — версии и alias этих зависимостей.
- `app/src/main/AndroidManifest.xml` — launcher Activity, тема экрана.
- `app/src/main/res/values/strings.xml` — английские тексты интерфейса, как в PDF.

Добавлены:

- `app/src/main/java/com/rslnabk/aivideotest/MainActivity.kt`
- `app/src/main/java/com/rslnabk/aivideotest/AppViewModel.kt`
- `app/src/main/java/com/rslnabk/aivideotest/model/Catalog.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/CatalogRepository.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/demo/DemoCatalogRepository.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/demo/DemoSession.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/demo/PreferencesDemoStore.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/catalog/BrowserFragment.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/effect/EffectFragment.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/common/CatalogViews.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/common/DemoDialogs.kt`
- `app/src/main/res/layout/activity_main.xml`, `fragment_browser.xml`, `fragment_effect.xml`
- `app/src/main/res/menu/main_navigation.xml`
- `app/src/main/res/values/screen_styles.xml`, `ids.xml`
- `app/src/main/res/drawable/bg_circle.xml`, `ic_back.xml`, `ic_clock.xml`, `ic_heart.xml`, `ic_heart_outline.xml`, `ic_photo.xml`, `ic_prompt.xml`, `ic_settings.xml`, `ic_sparkle.xml`, `ic_video.xml`
- `app/src/main/res/drawable-nodpi/demo_anime_1.webp`, `demo_anime_portrait.webp`, `demo_banner.webp`, `demo_beach.webp`, `demo_fashion.webp`, `demo_gold.webp`, `demo_japan.webp`, `demo_penguin.webp`, `demo_portrait.webp`, `demo_underwater.webp`
- `app/src/test/java/com/rslnabk/aivideotest/DemoSessionTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/CatalogNavigationTest.kt`
- `docs/demo-assets.json`, `docs/first-batch-report.md`, `tools/extract_demo_assets.py`

Обновлён статус `docs/screen-implementation-plan.md`. Существовавшие изменения токенов / типографики и их состояние в Git сохранены.

## Проверка

- Финальная `assembleDebug` и `lintDebug` выполнены успешно. Lint: 0 ошибок; 98 предупреждений — 89 неиспользуемых ресурсов ранее добавленной дизайн-системы / заготовок и 9 уведомлений о версиях инструментов / зависимостей.
- Unit tests: 5 успешно, включая 4 новых проверки общего demo-состояния, восстановления избранного, независимости PRO / баланса и состава каталога.
- Эмулятор Pixel 7 / Android 14 (API 34): 4 instrumentation tests успешно, включая 3 новых сценария. Проверены каталог → категория → эффект → избранное, Like / Unlike, раздельные Photo / Video, пустая Library, начальный Settings, восстановление эффекта после пересоздания Activity, сохранение избранного после нового запуска, возврат к категории / прокрутке, переключение вкладок и восстановление прокрутки после пересоздания.
- Каталог, категория и Effect дополнительно просмотрены по снимкам из финальной APK после совместимой замены XML paddingHorizontal на paddingStart / paddingEnd.
- Промежуточный повторный запуск тестов был прерван отключением эмулятора. После запуска отдельного эмулятора весь набор прошёл успешно.
- На API 24 и API 37 отдельный runtime-прогон пока не выполнялся. Поворот покрыт пересозданием Activity; реальный переход в landscape и принудительное восстановление всего процесса на вложенном экране отдельно не проверялись.
- `git diff --check` без ошибок. Исходные staged-изменения дизайн-системы сохранены. Коммит не создавался.

Для обзора подготовлены `AiVideoTest-first-batch.apk` и снимки `catalog.png`, `category.png`, `effect.png`. В debug-сборке открыть demo-controls можно долгим нажатием на баланс.
