# Шестая порция: P7, проверка demo и визуальная доводка

Дата: 6 октября 2026. Основа: `fd39f2b`. Работа относится к A27–A30. UI остаётся Jetpack Compose, один модуль `app`; MainActivity, AppViewModel, DemoSession, storage и платформенные Activity Result-контракты сохраняют свои роли.

## Исправления

- Нижняя навигация измеряет подписи с действующим масштабом шрифта. На опорном размере остаётся одна строка, на узком экране с крупным шрифтом вкладки переносятся. Все пять разделов доступны; подписи целиком, выбранное состояние и роль Tab сохранены, touch targets не меньше 48dp. Font scale и исходные токены не переопределяются.
- Корневой заголовок измеряет доступную ширину вместе с балансом. При нехватке места баланс переходит на отдельную строку; Settings не превращается в «Sett…» при 200%. Детальные экраны сохраняют Back.
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

- Полный прогон до последних правок заголовка и video poster: 43 instrumentation tests на Android 14 / API 34, 0 failures/errors/skipped. 33 unit tests, 0 failures/errors/skipped. Добавлены ещё две regression-проверки крупного заголовка; финальный прогон 45 тестов пока не выполнен.
- Debug, AndroidTest и unsigned optimized Release до последнего изменения заголовка успешно собраны. Lint: 0 errors, 33 warnings, 1 hint. Предупреждения о прежних зависимостях/общих компонентах/unused resources не устранены обновлением всего проекта.
- Ручная матрица на изолированном API 34: onboarding, PRO/Tokens, обе Trends ветки, category/effect/instruction/photo sheets, Prompt, empty Favorites/Library, Settings, rating/review; 390×844dp. На 320×640dp, 150–200% проверены перенос нижних вкладок, читаемые counter/copy/clear, Settings и Close offers. При 200% найдено обрезание заголовка; исправление ожидает сборки и повторной проверки.
- Приложение действительно force-stopped на Creating и запущено снова: одна ready job, сохранённый prompt `Native Sunset`, баланс 90 после единственного списания с 100. Это проверка реального процесса, а не только recreate.
- В Native UI video сохранено в Gallery; Files и Share открыты и отменены; после force-stop Share повторно не открылся. Получатель не выбирался, сообщений не отправлялось. Byte/MIME/grant/rollback проверки фото и видео включены в автоматический suite.
- По визуальному кадру найдено дублирование poster под letterbox; исправление собрано, его окончательный native/device прогон остаётся незавершённым.
- Media inventory: 28 WebP — 2 129 586 bytes, 9 MP4 — 2 302 091 bytes. Последний собранный debug APK — 17 728 164 bytes; unsigned release до последней правки заголовка — 6 404 415 bytes. Размер не является измерением FPS/производительности на физическом телефоне.
- Runtime на API 36 ещё не проверен. Нельзя считать P7 финально подтверждённым: автоматическая проверка разрешения на сборку дважды завершилась таймаутом; в песочнице Gradle не может открыть глобальный wrapper/cache lock. Изменения заголовка и их tests сохранены, требуется возобновить сборку и проверки.

## Границы и неоднозначности

- Не добавлены реальные генерация/платежи/renewal/server push. Все видимые demo-действия используют существующие локальные adapters.
- PDF не содержит исходного motion/video. Effect остаётся Still preview без Scroll Down для одного кадра; Result воспроизводит короткий локальный clip. Для полного motion нужны исходные материалы.
- PRO/unlimited/баланс, Year/Week и одинаковые token packs остаются неоднозначностями источника. В demo PRO и токены независимы; retry возвращает стоимость failed один раз и повторяет immutable input. Эти правила не утверждены для коммерческого продукта.
- Источник фото ограничен одним reference вместо неоднозначного «up to 4». Support/Letter/Legal не имеют содержимого/URL в PDF и обозначены как local demo; внешние сообщения не отправлялись.
- После force-stop обычный новый запуск открывает AI Video, сохраняя черновики, history/favorites/account/preferences/операции. Это отличается от восстановления back stack при Android Activity recreation.
- Runtime на API 24–28 и 37, физический телефон, разные облачные document providers, убийство процесса посередине native camera и отказ физического накопителя остаются непроверенными. minSdk 24 и targetSdk 37 сохранены; сборка/lint не считаются заменой этих проверок.
- При прерывании записи в Files провайдер может оставить частичный документ; приложение показывает interrupted/error и предлагает повтор. Успешно экспортированные копии не удаляются вместе с историей.

## Изменённые файлы

15 файлов; пользовательские изменения `.idea` не включены.

- `app/src/androidTest/java/com/rslnabk/aivideotest/AdaptiveTabBarTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/DemoMatrixTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/PhotoThumbnailTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/PromptLayoutTest.kt`
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
