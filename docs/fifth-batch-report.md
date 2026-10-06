# Пятая порция: онбординг и Settings на Compose

Дата: 6 октября 2026. Закрыты P6 / A23–A26. Основа — коммит `51c12ea`; следующая порция P7 — общая сценарная матрица и визуальная доводка.

## Реализация

Первый запуск: Splash с lime-квадратом и индикатором, пять иллюстрированных шагов, Back, сохранение текущего шага и завершения. После завершения открывается PRO; ожидающий переход сохраняется и восстанавливается при холодном запуске; его закрытие возвращает к каталогу. Повторный запуск открывает приложение сразу. Для Android 12+ оформлен нативный Splash. Старые установленные демосборки пропускают новый онбординг, сохраняя историю, баланс, избранное и чеки. Debug → Replay onboarding повторяет знакомство без сброса данных; Reset demo сбрасывает все локальные демонстрационные данные.

На шаге отзывов доступны локальная оценка и выбор одного фото через системный Android Photo Picker, sample или Not now. Выбранное фото попадает в черновик AI Photo; отмена выбора разрешает продолжить. Загрузка фото использует прежний PhotoImporter, её ошибки и повтор доступны в редакторе. На шаге уведомлений Next запрашивает разрешение Android; отказ не блокирует знакомство. Повторный вход доступен в Settings, при блокировке — переход к настройкам приложения в ОС.

Settings: зелёная Free-карточка с More, зелёная PRO-карточка без More, красная карточка малого баланса. Crown открывает PRO в любом состоянии; красный More — Tokens. Общий баланс и восстановление подписки используют прежний DemoSession. Добавлены Share with friends, Like us, Rate us, Notification, Report a problem, Restore Purchases, Clear Cache, Letter, Privacy Policy, Terms & Conditions и реальная версия приложения.

Like us открывает экран с сердцами и No / Yes / Close; Yes ведёт к оценке 1–5. Rate us открывает форму напрямую. Отзывы, сообщения Report / Letter и незавершённые тексты сохраняются локально. Заголовок отзыва до 80 и текст до 1000 Unicode code points; отправка без оценки или пустого сообщения недоступна. Подтверждение — «Saved on this device». Нет отправки письма, внешней поддержки, публикации отзыва или настоящего договора. Share with friends открывает реальный Android sharesheet с описанием демоприложения; неизвестный магазинный URL не придуман.

Уведомления имеют раздельные preference и OS permission. Switch показывает фактическую доступность; отзыв разрешения ОС не удаляет preference. При первом включении приходит локальное «Fresh update!», затем завершённые будущие задачи могут дать локальное уведомление с переходом в Result. Старую историю включение не объявляет. Принятые события сохраняются перед доставкой, поэтому поворот / новый ViewModel не повторяет их. Это уведомления внутри локального демосценария: без push-сервера / фонового сервиса; при остановленном процессе задача сверяется и уведомление выдаётся при следующем запуске.

Clear Cache подтверждается диалогом с красным Clear. Размер берётся с диска; обычно 0 B, поскольку изображения и видео демо входят в APK. Удаляется только `cacheDir/previews`. Reference photos в filesDir, camera и cacheDir/exports исключены, включая ссылки на них. History / Favorites / account / receipts не меняются. Есть Busy, Cancel, Success, Error / Retry; следующий отказ можно включить через debug.

Весь UI приложения остаётся Jetpack Compose / Material 3. Общий LocalContentColor установлен в labelPrimary, чтобы неуказанный цвет текста корректно наследовался на тёмном фоне. Палитра и числовые роли SF Pro из дизайн-системы сохранены; шрифт остаётся документированным Android sans-serif fallback. В MainActivity находятся только Android-интеграции, состояние — в DemoSession / DemoSnapshot; добавлен preferences_v1 со стабильными именами состояний. Принятые шаги, feedback и уведомления сохраняются синхронно, ввод — прежним асинхронным путём.

## Источник и неоднозначности

Просмотрены страницы 5–12, 25–26, 326, 328, 334–337, 339–340 PDF `157 - Higgsfield AI  (Vlad, 06.08.2025) (Copy) (Copy).pdf`. Шесть WEBP содержат только иллюстрации, без внешней статусной строки, реальных заголовков и кнопок; элементы внутри нарисованного телефона — часть иллюстрации. Координаты, страницы и SHA256 — в `fifth-batch-assets.json`.

| Неоднозначность | Принято для демонстрации |
| --- | --- |
| iOS Photos / AppStore / notification dialogs | Android Photo Picker, настоящие notification permission / system settings и явно локальный Compose review; iOS-окна не имитируют действующее системное разрешение |
| PDF не задаёт момент фото-доступа вне кадра 10 | Sheet поверх шага отзывов; отмена / sample / skip продолжает знакомство |
| На зелёной и красной карточках одинаковый баланс 5 | Красная при tokens < 10, иначе PRO или Free; это demo-правило, не выведенная коммерческая политика |
| PRO остаётся платным в токенах | Сохраняется принятая независимость подписки и баланса |
| Like us, Rate us в PDF объединены | Два явных входа: приглашение с сердцами и непосредственная форма оценки |
| Cache «5 MB» и Version V5.3.5 — статичные примеры | Фактический размер удаляемых previews и BuildConfig.VERSION_NAME (1.0) |
| Нет support/store/email URLs и юридических текстов | Локальные формы и пояснение демо; ссылки и договоры не выдуманы |
| Нет лимитов/сервера отзывов | Локальная оценка, 80 / 1000 code points, максимум 20 сохранённых обращений; это временные правила демо |
| Статичные onboarding-изображения | Извлечены из PDF, анимации не реконструированы по одному кадру |

Android-адаптация опирается на [официальную документацию Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission) и [Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker): для выбранного изображения не нужен доступ ко всей галерее; на API 33+ запрашивается POST_NOTIFICATIONS.

## Проверки

Успешны assembleDebug, testDebugUnitTest, connectedDebugAndroidTest и lintDebug.

- 33 unit tests: 24 прежних + 9 новых. Новые проверки покрывают переходы/повторы онбординга и ожидающий offer, opt-in / opt-out и однократные notification claims, ошибки генерации, Unicode-валидацию и сохранение feedback, три карточки, cache/history separation и symlink isolation.
- Полный прогон на отдельном AiVideoP6 / Android 14 (API 34): 32 instrumentation tests, 0 failures / errors / skipped. 23 прежних + 9 новых: Preferences round-trip / upgrade, первый запуск / Back / rotation / cold restore / sample / skip / pending offer, review / support draft / save, cache Cancel / injected Error / Retry с сохранённой историей и фото, legal / version, включение уведомлений, одно preview, ready event / tap в Result, rotation и холодный Intent. Старые fixtures явно помечены завершённым онбордингом.
- Lint: 0 errors, 33 warnings. Оставшиеся замечания о версиях зависимостей, старых общих компонентах / unused resources; новых API-ошибок нет.
- git diff HEAD --check успешен. Коммит в этой порции не создавался.

Визуально сверены все пять шагов, photo sheet / Android picker, permission dialog, Settings top / bottom, cache confirmation, review / report / letter. На 320×640dp со шрифтом 150% проверены onboarding и sheet / Next через прокрутку, форма отзыва и Yes / No. Исправлено наследование цвета текста; unchecked Switch использует цвета дизайн-системы.

Отдельно от instrumentation-раннера проверены настоящие Android Allow / Don’t allow, отмена Photo Picker, отзыв POST_NOTIFICATIONS после opt-in и перезапуск: Switch выключен, preference сохранена, есть пояснение о блокировке ОС. Share открывает native sharesheet; ни адресат, ни отправка не выбирались.

После последнего уточнения цветов Switch и обработки недоступного cache dir повторно прошли сборка, 33 unit tests и lint; эти состояния также просмотрены на устройстве. Полная матрица layout остаётся P7: на компактном экране со шрифтом 150% подписи прежней нижней навигации обрезаются, сами вкладки доступны. Ошибки физического накопителя воспроизводились только через demo failure; сохранение истории/фото и запрет обхода через symlink проверены автоматически. API 24 / 37, весь landscape и полная визуальная матрица прежних экранов остаются P7.

## Как проверить

1. Чистая установка → Next по шагам, Back, перезапуск посередине, sample / отмена Picker, разрешить уведомления или Not now → PRO → Close.
2. Settings → Like us → No / Yes / Close; Rate us → звёзды / ввод / Back / возврат / Save. Report / Letter аналогично сохраняют локальный текст.
3. Notification → Allow / отказ. Отключить в Android settings и вернуться: фактический Switch выключен. После включения запустить генерацию, дождаться уведомления и открыть результат.
4. Clear Cache → Cancel / Clear. Debug → Next cache clear: error → Retry; история и фотографии сохраняются.
5. Debug Free / PRO / zero задаёт карточку (для зелёной достаточно ≥10 токенов). Crown → PRO, красный More → Tokens, Restore → прежний локальный сценарий покупки.

## Изменённые файлы

Относительно `51c12ea`, 52 файлов, без настроек IDE:

- `app/src/androidTest/java/com/rslnabk/aivideotest/ComposeFlowTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/LibraryFlowTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/NotificationFlowTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/PreferencesFlowTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/PreferencesPersistenceTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/PurchaseFlowTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/ResultExportTest.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/rslnabk/aivideotest/AppViewModel.kt`
- `app/src/main/java/com/rslnabk/aivideotest/MainActivity.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/demo/DemoSession.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/demo/PreferencesDemoStore.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/demo/PreferencesJson.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/settings/DemoNotifications.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/settings/PreviewCache.kt`
- `app/src/main/java/com/rslnabk/aivideotest/model/Catalog.kt`
- `app/src/main/java/com/rslnabk/aivideotest/model/Preferences.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/catalog/BrowserScreen.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/common/AppDialogs.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/common/Components.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/navigation/AiVideoApp.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/onboarding/OnboardingScreen.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/settings/FeedbackScreen.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/theme/AiVideoTheme.kt`
- `app/src/main/res/drawable-nodpi/demo_intro_notifications.webp`
- `app/src/main/res/drawable-nodpi/demo_intro_prompt.webp`
- `app/src/main/res/drawable-nodpi/demo_intro_reviews.webp`
- `app/src/main/res/drawable-nodpi/demo_intro_share.webp`
- `app/src/main/res/drawable-nodpi/demo_intro_welcome.webp`
- `app/src/main/res/drawable-nodpi/demo_rating_hearts.webp`
- `app/src/main/res/drawable/demo_splash_mark.xml`
- `app/src/main/res/drawable/ic_bell.xml`
- `app/src/main/res/drawable/ic_close.xml`
- `app/src/main/res/drawable/ic_crown.xml`
- `app/src/main/res/drawable/ic_legal.xml`
- `app/src/main/res/drawable/ic_letter.xml`
- `app/src/main/res/drawable/ic_report.xml`
- `app/src/main/res/drawable/ic_restore.xml`
- `app/src/main/res/drawable/ic_share.xml`
- `app/src/main/res/drawable/ic_star.xml`
- `app/src/main/res/drawable/ic_trash.xml`
- `app/src/main/res/values-v31/themes.xml`
- `app/src/main/res/values/preferences_strings.xml`
- `app/src/test/java/com/rslnabk/aivideotest/PreferencesSessionTest.kt`
- `app/src/test/java/com/rslnabk/aivideotest/PreviewCacheTest.kt`
- `docs/design-system.md`
- `docs/fifth-batch-assets.json`
- `docs/fifth-batch-report.md`
- `docs/pdf-screen-inventory.md`
- `docs/screen-implementation-plan.md`
- `tools/extract_onboarding_assets.py`
 Изменения `.idea/gradle.xml` и `.idea/misc.xml` существовали до задачи и не относятся к этой порции.

## Сборка и screenshots

APK: `AiVideoTest-fifth-batch.apk`, 17728164 байт. SHA256: `30fe967f3e52aa8ad12f64d2917092a0945bb53fe781d19a9ba581a6896d77ab`.

Screenshots: каталог `fifth-batch/`; обзор — `fifth-batch-preview.png`.

[Обзор новых экранов](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch-preview.png)

- [01-intro-welcome](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/01-intro-welcome.png)
- [02-intro-prompt](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/02-intro-prompt.png)
- [03-intro-share](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/03-intro-share.png)
- [04-intro-reviews](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/04-intro-reviews.png)
- [05-intro-photos](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/05-intro-photos.png)
- [06-android-photo-picker](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/06-android-photo-picker.png)
- [07-intro-notifications](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/07-intro-notifications.png)
- [08-android-notification-permission](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/08-android-notification-permission.png)
- [09-onboarding-pro](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/09-onboarding-pro.png)
- [10-settings-low-balance](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/10-settings-low-balance.png)
- [11-settings-bottom](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/11-settings-bottom.png)
- [12-cache-confirmation](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/12-cache-confirmation.png)
- [13-demo-review](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/13-demo-review.png)
- [14-demo-report](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/14-demo-report.png)
- [15-demo-letter](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/15-demo-letter.png)
- [16-notification-revoked](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/16-notification-revoked.png)
- [17-compact-settings](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/17-compact-settings.png)
- [18-compact-review](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/18-compact-review.png)
- [19-compact-rating](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/19-compact-rating.png)
- [20-compact-rating-actions](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/20-compact-rating-actions.png)
- [21-android-share-app](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/21-android-share-app.png)
- [22-compact-intro](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/22-compact-intro.png)
- [23-compact-intro-reviews](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/23-compact-intro-reviews.png)
- [24-compact-intro-actions](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/24-compact-intro-actions.png)
- [25-compact-photo-sheet](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/25-compact-photo-sheet.png)
- [26-settings-pro](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/26-settings-pro.png)
- [27-settings-free](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/27-settings-free.png)
- [28-final-review](/Users/kurban/Documents/Codex/2026-10-05/referenced-chatgpt-conversation-this-is-an/outputs/fifth-batch/28-final-review.png)
