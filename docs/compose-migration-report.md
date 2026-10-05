# Миграция AiVideoTest на Jetpack Compose

Дата: 6 октября 2026. Основа: коммит `06d5ca4`, завершённые порции 1–3.

## Результат

Все реализованные экраны, компоненты, диалоги и sheets перенесены на Jetpack Compose / Material 3. Главная Activity теперь ComponentActivity с setContent; навигация — NavHost / Navigation Compose. Fragment, ViewBinding, XML-разметки экранов и зависимости AppCompat / Material Components удалены.

Перенесены:

- Video / Photo Trends, баннер, секции, категории, карточки и избранное.
- Prompt Video / Photo: ввод, Unicode-счётчик, валидация, Copy / Clear, стили, разрешение, фотографии и восстановление после ошибки.
- Просмотр эффекта и подготовка фотографии; Instruction / Source / Samples sheets.
- Creating / Failed / Retry и переход к результату либо в Library.
- Favorites / Library: фильтры, пустые состояния, готовые / создающиеся / ошибочные результаты, повтор и удаление.
- Result: фото / видео, Play / Pause, меню Save / Files / Delete, Share, подтверждения, ошибки и повтор экспорта.
- Текущая заглушка Settings, баланс, демотокены и debug-панель.

Порции PRO / Tokens / Onboarding / полные Settings пока остаются следующими этапами исходного плана.

## Данные и архитектура

AppViewModel, DemoSession, репозиторий, импорт фотографий и ExportController сохранены. Формат demo_state_v1, generation_v1 и result_export_v1 не изменён: баланс, избранное, черновики и история совместимы с прежней версией.

Навигация сохраняет состояния корневых вкладок и прокрутку. Фильтры хранятся в SavedStateHandle; UI-диалоги, выбранная вкладка и состояние воспроизведения — в rememberSaveable. Системные камера, галерея, файловый picker, разрешение и sharing используют прежние Activity Result-контракты. Share запускается на возобновлённой Activity после однократного claimShare.

Видео использует платформенный VideoView через небольшой AndroidView-компонент внутри Compose. Это единственный мост к View в UI; XML-экраны и Fragment не используются. Плеер сохраняет паузу и позицию и приостанавливается при уходе приложения в фон.

## Дизайн-система

AiVideoTheme читает все 28 цветовых токенов из design_colors.xml и 34 типографические роли из typography_dimens.xml. Значения PDF, включая accent/primary #d1fe17 и accent/secondary #a9fe17, сохранены. Высоты основных кнопок и радиусы общих компонентов берутся из component_dimens.xml. Исходные sp читаются без предварительного масштабирования.

Неоднозначности остаются прежними: SF Pro не приложен как разрешённый для Android шрифт, используется системный sans-serif; необычные размеры Body / Callout / Footnote и совпадающие цветовые токены сохранены буквально. Material 3 применяет собственные правила размещения и минимальных touch targets; пиксельное совпадение со старой Views-версией не заявляется. Динамическая системная палитра отключена.

Debug XML-preview заменён DesignSystemPreview.kt: роли шрифтов, палитра и Compose-компоненты доступны в Preview Android Studio.

## Проверки

- assembleDebug, assembleDebugAndroidTest, testDebugUnitTest и lintDebug завершились успешно.
- 17 модульных тестов: все прошли.
- Полный прогон 16 инструментальных тестов на Pixel 7 / Android 14 API 34: все прошли. Перенесены 12 UI-тестов Compose, сохранены проверки данных и реальных экспортируемых JPEG / MP4.
- Проверены переходы, scroll / origin, синхронизация избранного, ввод и лимит запроса, нехватка токенов, импорт, ошибка / повтор генерации, фильтры, удаление / отмена, ошибки и повтор экспорта, восстановление при пересоздании Activity.
- Финальная сборка после добавления Preview и удаления ненужных ресурсов снова прошла сборку, модульные тесты и lint: 0 ошибок, 30 предупреждений, 1 подсказка. Полный device-прогон был выполнен до этих последних изменений; поведение экранов после него не менялось.
- Визуально просмотрены каталог, Photo Prompt и инструкция на отдельном эмуляторе; скриншоты включены в комплект миграции.
- git diff --check: без ошибок.

Физическое устройство, Android API 24, landscape, увеличенный шрифт и разные сторонние файловые провайдеры в рамках этой миграции отдельно не проверялись. Реальные серверная генерация и оплата не добавлялись — приложение остаётся демонстрационным.

## Зависимости и документация

Compose BOM 2026.09.00, Compose Compiler / Kotlin 2.4.20, Activity Compose 1.13.0 и Navigation Compose 2.10.2. Настройка сверена с [официальной документацией Compose Compiler](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler) и [встроенного Kotlin в AGP](https://developer.android.com/build/migrate-to-built-in-kotlin). Версии Activity и Navigation проверены по [Activity releases](https://developer.android.com/jetpack/androidx/releases/activity) и [Navigation releases](https://developer.android.com/jetpack/androidx/releases/navigation).

Новый коммит в рамках миграции не создавался. Изменения локальных настроек .idea, оставшиеся до начала миграции, не относятся к её реализации.

## Изменённые файлы

<!-- file-list -->
63 файла: добавленные, обновлённые и удалённые при миграции.

- `app/build.gradle.kts`
- `app/src/androidTest/java/com/rslnabk/aivideotest/CatalogNavigationTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/ComposeFlowTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/GenerationFlowTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/LibraryFlowTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/ResultExportTest.kt`
- `app/src/debug/java/com/rslnabk/aivideotest/ui/theme/DesignSystemPreview.kt`
- `app/src/debug/res/layout/design_system_preview.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/rslnabk/aivideotest/MainActivity.kt`
- `app/src/main/java/com/rslnabk/aivideotest/model/Catalog.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/catalog/BrowserFragment.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/catalog/BrowserScreen.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/common/AppDialogs.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/common/CatalogViews.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/common/Components.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/common/DemoDialogs.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/effect/EffectFragment.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/effect/EffectScreen.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/generator/CreatingFragment.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/generator/CreatingScreen.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/generator/GeneratorFragment.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/generator/PhotoDialogs.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/generator/PromptEditor.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/library/CreationDialogs.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/library/GenerationCard.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/navigation/AiVideoApp.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/result/ExportStatusDialog.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/result/ResultFragment.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/result/ResultScreen.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/theme/AiVideoTheme.kt`
- `app/src/main/res/color/ds_button_primary_background.xml`
- `app/src/main/res/color/ds_button_primary_content.xml`
- `app/src/main/res/color/ds_card_background.xml`
- `app/src/main/res/color/ds_card_stroke.xml`
- `app/src/main/res/color/ds_generate_content.xml`
- `app/src/main/res/color/ds_input_stroke.xml`
- `app/src/main/res/color/ds_label_content.xml`
- `app/src/main/res/color/ds_navigation_content.xml`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/fragment_browser.xml`
- `app/src/main/res/layout/fragment_creating.xml`
- `app/src/main/res/layout/fragment_effect.xml`
- `app/src/main/res/layout/fragment_generator.xml`
- `app/src/main/res/layout/fragment_result.xml`
- `app/src/main/res/layout/view_flow_header.xml`
- `app/src/main/res/layout/view_prompt_editor.xml`
- `app/src/main/res/menu/main_navigation.xml`
- `app/src/main/res/values-night/themes.xml`
- `app/src/main/res/values-v28/font_families.xml`
- `app/src/main/res/values-v28/typography.xml`
- `app/src/main/res/values/components.xml`
- `app/src/main/res/values/font_families.xml`
- `app/src/main/res/values/ids.xml`
- `app/src/main/res/values/photo_sheet_styles.xml`
- `app/src/main/res/values/screen_styles.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values/typography.xml`
- `build.gradle.kts`
- `docs/compose-migration-report.md`
- `docs/design-system.md`
- `docs/screen-implementation-plan.md`
- `gradle/libs.versions.toml`
