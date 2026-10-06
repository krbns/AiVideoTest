# Шестая порция: P7, проверка demo и визуальная доводка

Дата: 6 октября 2026. Основа: `fd39f2b`; основные исправления зафиксированы в `76cdc5a`, дополнительная адаптация Prompt под горизонтальную клавиатуру и итоговые отчёты сохранены в рабочем дереве. Работа относится к A27–A30. UI остаётся Jetpack Compose, один модуль `app`; MainActivity, AppViewModel, DemoSession, storage и платформенные Activity Result-контракты сохраняют свои роли.

## Исправления

- Нижняя навигация измеряет подписи с действующим масштабом шрифта. На опорном размере остаётся одна строка, на узком экране с крупным шрифтом вкладки переносятся. Все пять разделов доступны; подписи целиком, выбранное состояние и роль Tab сохранены, touch targets не меньше 48dp. Font scale и исходные токены не переопределяются.
- Корневой заголовок измеряет доступную ширину вместе с балансом. При нехватке места баланс переходит на отдельную строку; Settings не превращается в «Sett…» при 200%. Детальные экраны сохраняют Back.
- При доступной высоте Prompt менее 300dp заголовок и переключатель прокручиваются вместе с формой: горизонтальная клавиатура больше не оставляет редактору нулевую высоту. Добавлен сценарий ввода и доступа к действиям при viewport 90dp.
- Счётчик Prompt, Copy/Clear переносятся при нехватке ширины. Карточки стилей учитывают ширину увеличенной подписи, а ряд сохраняет горизонтальную прокрутку.
- По опорным страницам PDF исправлены активные сегменты Trends/Prompt и Photos/Videos: лаймовая заливка, инвертированные подписи/иконки, иконка над подписью. Заголовок карточки Prompt стал акцентным; поле не добавляет отсутствующую в PDF чёрную внутреннюю подложку.
- Крестик закрытия PRO/Tokens — существующая vector icon с описанием Close и 48dp областью, поэтому крупный шрифт не обрезает управляющий символ.
- Импортированное фото для 80dp reference preview декодируется под фактический размер, на IO-потоке. Исходник/EXIF-нормализация/экспорт не меняются. Проверка на JPEG 1600×1200 → thumbnail 200×200 подтверждает минимум 16-кратное снижение bitmap allocation и неизменность байтов исходника.
- Устранено дублирование картинки по краям видеорезультата: статичный poster виден только до готовности плеера. После подготовки остаётся один ролик с сохранением его пропорций, без второго изображения под letterbox.
- Добавлены проверки всех четырёх путей генерации, их snapshots/цен/сохранения/истории, реального playback pause → recreate → background → resume, размеров навигации и полных Prompt controls при 150–200%.
- `demo-coverage.md` связывает все 16 семейств, состояния, страницы PDF, реализации и доказательства проверки; сохраняет открытые вопросы и границы demo.

## Источник и визуальная проверка

Источник — исходный PDF из Downloads, 340 страниц. Для этой порции повторно отрендерены опорные страницы 5, 6, 13, 19, 25, 43, 48, 59, 97, 99, 117–120, 240–241, 326, 328; остальные состояния сопоставлены с реестром и отчётами предыдущих порций.

Цветовые токены, включая accent/primary `#d1fe17`, accent/secondary `#a9fe17`, background/label, и все 34 роли типографики сохранены. SF Pro не предоставлен для Android, применяется центральный SansSerif fallback. Строки названий эффектов, Android системные окна, динамическая высота областей и перенос навигации — документированные адаптации. Пиксельное совпадение iOS-макетов со всеми устройствами не заявляется.

## Проверки

- Финальный полный прогон: **46 instrumentation tests на Android 14 / API 34 и 46 на Android 16 / API 36**, в каждом 0 failures/errors/skipped. Включены новые проверки заголовка, poster и Prompt при доступной высоте 90dp. **33 unit tests**, 0 failures/errors/skipped.
- Финальные Debug, AndroidTest и unsigned optimized Release успешно собраны. Lint: 0 errors, 34 warnings, 1 hint. Остались 24 unused resources, предупреждения о зависимостях и прежних общих компонентах; обновление всего проекта не входит в эту порцию.
- Ручная матрица на изолированном API 34: onboarding, PRO/Tokens, обе Trends ветки, category/effect/instruction/photo sheets, Prompt, empty Favorites/Library, Settings, rating/review. Финальная сборка повторно снята при 390×844dp и 320×640dp с font scale 200%; доступны вкладки, полный Settings, ввод над клавиатурой, Generate и нижние настройки. Counter/Copy/Clear при 150–200% дополнительно проверены автоматическими tests.
- Горизонтальный viewport 640×320dp: форма прокручивается, введённый текст и курсор видны над IME. Проверен также настоящий Android user_rotation 0→1→0 с Activity recreation: текст сохранён, ввод продолжен; скриншоты 55–56. Video воспроизводится с сохранением пропорций, Pause доступен; дублирование poster по краям устранено и подтверждено финальными native кадрами и device tests.
- Приложение действительно force-stopped на Creating и запущено снова: одна ready job, сохранённый prompt `Native Sunset`, баланс 90 после единственного списания с 100. Эта ручная проверка выполнена до последних layout-правок; domain/storage не менялись.
- В Native UI video сохранено в Gallery; Files и Share открыты и отменены; после force-stop Share повторно не открылся. Получатель не выбирался, сообщений не отправлялось. Byte/MIME/grant/rollback проверки фото и видео входят в финальные suites обоих API.
- Media inventory: 28 WebP — 2 129 586 bytes, 9 MP4 — 2 302 091 bytes. Финальный debug APK — **17,196,461 bytes**; unsigned release — **6,420,799 bytes**. Debug подпись проверена `apksigner`; release не выдаётся как установочный файл. Размер не является измерением FPS/производительности на физическом телефоне.
- APK SHA-256: `56f6538e5bd465363c69fb902c5630ec3470cd0ac2993f760cb2e1f63a8d125c`. В комплекте — финальные native screenshots, обзор, XML обоих device-прогонов и unit tests, lint XML и `validation.json`. Снимки ранних неудачных раскладок и промежуточного poster не включены.
- **P7 завершён для согласованного этапа с демонстрационными данными**, в пределах проверки Android 14/16 и документированных ниже ограничений. Реестр связывает все 16 семейств исходника с реализацией и проверяемыми сценариями.

## Границы и неоднозначности

- Не добавлены реальные генерация/платежи/renewal/server push. Все видимые demo-действия используют существующие локальные adapters.
- PDF не содержит исходного motion/video. Effect остаётся Still preview без Scroll Down для одного кадра; Result воспроизводит короткий локальный clip. Для полного motion нужны исходные материалы.
- PRO/unlimited/баланс, Year/Week и одинаковые token packs остаются неоднозначностями источника. В demo PRO и токены независимы; retry возвращает стоимость failed один раз и повторяет immutable input. Эти правила не утверждены для коммерческого продукта.
- Источник фото ограничен одним reference вместо неоднозначного «up to 4». Support/Letter/Legal не имеют содержимого/URL в PDF и обозначены как local demo; внешние сообщения не отправлялись.
- После force-stop обычный новый запуск открывает AI Video, сохраняя черновики, history/favorites/account/preferences/операции. Это отличается от восстановления back stack при Android Activity recreation.
- Runtime на API 24–28 и 37, физический телефон, разные облачные document providers, убийство процесса посередине native camera и отказ физического накопителя остаются непроверенными. minSdk 24 и targetSdk 37 сохранены; сборка/lint не считаются заменой этих проверок.
- При прерывании записи в Files провайдер может оставить частичный документ; приложение показывает interrupted/error и предлагает повтор. Успешно экспортированные копии не удаляются вместе с историей.

## Проверка вручную

Установочный файл — `AiVideoTest-sixth-batch.apk`, debug-сборка. Новая установка начинает onboarding; существующая сохраняет demo-данные. После onboarding долгое нажатие на баланс открывает Demo controls: Free/PRO/нулевой баланс, Reset, ошибки следующего фото/генерации/экспорта/покупки/кэша и повтор onboarding. Панель не входит в release.

## Изменённые файлы

После `76cdc5a` изменены 6 файлов: BrowserScreen, PromptLayoutTest и четыре документа ниже. Всего 16 файлов в P7, включая дополнительную адаптацию BrowserScreen; пользовательские изменения `.idea` не включены.

- `app/src/androidTest/java/com/rslnabk/aivideotest/AdaptiveTabBarTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/DemoMatrixTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/PhotoThumbnailTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/PromptLayoutTest.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/catalog/BrowserScreen.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/media/PhotoThumbnail.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/common/Components.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/generator/PromptEditor.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/navigation/AiVideoApp.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/navigation/AppTabBar.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/offers/OfferScreen.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/result/ResultScreen.kt`
- `docs/demo-coverage.md`
- `docs/pdf-screen-inventory.md`
- `docs/screen-implementation-plan.md`
- `docs/sixth-batch-report.md`
