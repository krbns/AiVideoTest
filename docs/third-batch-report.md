# Третья порция: Library и действия с результатами

Дата: 6 октября 2026. Пакет P4, A15–A19. Android Views / XML, Fragment, ViewBinding и один модуль `app` сохранены. Предыдущие изменения второй порции в индексе Git не перезаписывались и не коммитились.

## Что реализовано

- Library: двухколоночная сетка с миниатюрой и названием исходного запроса / эффекта, отдельные Photos / Videos, порядок от новых к старым. Running отображается размытой миниатюрой со спиннером, Failed — с Generation error / Read more, Ready открывает тот же Result по ID. Фильтр и прокрутка сохраняются при возврате и пересоздании Activity.
- Пустые состояния Library / Favorites с иллюстрациями из PDF. Start creating открывает Prompt соответствующего типа. Заголовки нормализованы до Library / Favorites вместо несогласованных History / Wishlist в PDF.
- Failed action sheet: Refresh / Delete / Cancel. Refresh повторяет **снимок исходного запроса**, даже если пользователь уже изменил Prompt; строка и ID истории сохраняются. Списание один раз на принятую попытку; ошибка возвращает стоимость один раз. Если не хватает баланса, действует прежнее локальное Add demo tokens.
- Result menu: Save / Save to files / Delete. Нижняя Share открывает системное меню Android с настоящим локальным JPEG / MP4. Передаётся показанный demo-result, а не reference photo. Отправка получателю не производится автоматически; закрытие share sheet не считается успешной отправкой.
- Save: запись в MediaStore (Pictures / Movies → AiVideoTest на Android 10+). JPEG для фото, исходный MP4 для видео. Android 10+ не запрашивает доступ ко всей галерее; Android 7–9 требует разрешение записи. Незавершённая запись на Android 10+ скрыта через IS_PENDING, при обычной ошибке созданная запись удаляется.
- Save to files: Android ACTION_CREATE_DOCUMENT, выбор места пользователем, запись после получения URI. Отмена оставляет результат и баланс без изменений; повтор открывает новый выбор места. При обычной ошибке предпринимается удаление только нового пустого / частично записанного документа; возможность удаления зависит от document provider.
- Общий export controller выполняет IO вне основного потока и блокирует повторные действия во время операции. Success появляется после завершения записи. Failed предлагает Okay / Refresh. Debug → Fail next export позволяет воспроизвести ошибку записи в галерею, файлы или подготовки Share.
- Состояние экспорта хранится в небольшом журнале. Activity recreation не создаёт вторую операцию. После нового процесса прерванная запись не выдаётся за успех; её можно повторить. Share intent помечается потреблённым до открытия системного меню. При холодном запуске потерянный выбор места отменяется, чтобы интерфейс не оставался заблокированным.
- Delete требует подтверждения. Cancel не меняет историю. Удаление готовой / ошибочной записи сохраняется, не меняет баланс и Favorites, очищает только соответствующий activeJobId. Файлы, уже сохранённые в галерею / файлы / подготовленные для Share, не удаляются. Из Result удаление ведёт в Library нужного типа; из Library сохраняется её положение.
- Running нельзя удалить: задача завершается по прежнему сохранённому сроку. Долгий тап по Ready открывает Delete, по Failed — action sheet. Политика отмены активной генерации в PDF не определена и в этой порции не добавлялась.
- Favorites: Photos / Videos, empty / content, вход в Effect, синхронное удаление лайка из Effect / каталога / Favorites. Favorites остаются любимыми эффектами, отдельно от результатов Library.

## Сверка с PDF и принятые адаптации

Источник: полный PDF на 340 страниц из Downloads. Для этой порции визуально проверены страницы 101, 108, 113, 115, 122–123, 240–241, 244, 288, 291, 293, 295, 297–298, 304. Иллюстрации пустых состояний извлечены из векторных объектов на 240–241; происхождение и SHA256 — `third-batch-assets.json`.

- В PDF меню и alerts используют iOS blur / action sheet; Android-версия использует Material popup / bottom sheet / dialog и штатные picker / share sheet. Цвета, роли типографики, акцент и красный Delete берутся из существующей дизайн-системы.
- Name заменён названием эффекта или исходным Prompt. У Failed исправлена опечатка Reed more → Read more. Подзаголовок action sheet, ошибочно говорящий о добавлении фото, заменён объяснением повторного создания и возврата токенов.
- Ошибки Save в PDF описывают server not responding. Здесь экспорт локальный, поэтому текст сообщает об ошибке записи / доступа и предлагает повтор; сервер в демо не участвует.
- Refresh трактуется как повтор исходной failed-задачи в той же карточке. Цена и refund сохранены из P3; правил реального сервера в PDF нет.
- Сохранённые копии и история приложения независимы. Удаление не является отменой внешней отправки и не отзывает уже предоставленный системным приложениям файл.
- Используется Android font fallback из предыдущих порций: файл SF Pro не предоставлен. Числовые токены не менялись. Видеоролики остаются короткими локальными fixtures P3.

## Проверки

- `assembleDebug`, `testDebugUnitTest`, `lintDebug` успешны. Финальная сборка после последней правки цветов меню также проверена.
- 17 unit tests: 5 новых проверок Library / retry / delete и 12 ранее существовавших. Проверены исходный immutable-запрос при retry, тот же ID / одна карточка, однократное списание / refund, недостаток баланса, сохранение удаления и запрет удаления Running.
- На Pixel 7 / Android 14 (API 34) отдельно успешно выполнены 3 LibraryFlowTest, 4 ResultExportTest и 4 GenerationFlowTest — 11 сценариев. В этой сессии также прошли 3 проверки каталогов, тест JSON / Preferences и шаблонный instrumentation test в общих запусках. После исправлений новые классы проверены отдельными завершёнными запусками: один общий прогон был прерван тестовым раннером со status -1, до конца всей матрицы он не дошёл.
- Новые проверки включают обе ветки Gallery / Files / Share для фото и видео (JPEG декодируется, байты MP4 совпадают с fixture), MIME / ClipData / временный доступ, rollback failed-gallery, сохранённую копию после удаления истории, error → Refresh → success, cancellation / Activity recreation / cold launch, delete / cancel / last empty, filters и cross-tab likes.
- Lint: 0 ошибок, 98 предупреждений — 89 неиспользуемых ресурсов / заготовок и 9 уведомлений о версиях зависимостей / AGP. Новых предупреждений о реализации экспорта нет.
- Визуально просмотрены Library content / Running / Failed / empty, Favorites content / empty, menu / delete confirmation / failed action sheet, настоящее системное сохранение JPEG в Downloads с проверкой файла и Share preview с отменой. Снимки находятся в outputs/third-batch. Для нескольких карточек использованы контролируемые данные визуального обзора; стандартный новый запуск APK по-прежнему начинает с пустой истории.
- `git diff --check` успешен. Новый коммит не создавался.

Границы проверки: API 24–28 с разрешением записи, API 37, разные document providers / облачные диски, фактическая отправка получателю, process kill посередине внешнего picker / записи большого файла, landscape и крупный системный шрифт отдельно не прогонялись. При смерти процесса file provider может оставить частичный документ; приложение сообщает о прерывании и предлагает повтор, без автоматического удаления уже успешно экспортированных копий. Системные grant / callback и cleanup MediaStore требуют полного многоверсионного аудита в P7.

## Как проверить

1. Создать фото / видео через Prompt → Result. Menu → Save; затем Save to files → выбрать место. Share → системное меню → отменить либо выбрать приложение самостоятельно.
2. Result → Menu → Delete → Cancel; повторить → Delete. В Library виден результат удаления, после последней записи появляется empty.
3. Перед Generate включить долгим тапом по балансу Fail next creation. На Creating нажать Okay → Library → Read more → Refresh. Результат появляется в той же карточке.
4. В Library оставить готовую запись, долгий тап по балансу → Fail next export; открыть Result → Save → error → Refresh → success.
5. Favorites → Videos / Photos → Effect → снять Like → Back: карточка исчезает из Favorites, каталог показывает тот же лайк.

## Первичные справочники Android

Реализация опирается на официальные правила [MediaStore и доступа к медиа](https://developer.android.com/training/data-storage/shared/media), [выбора нового документа](https://developer.android.com/training/data-storage/shared/documents-files) и [передачи файлов через FileProvider](https://developer.android.com/training/secure-file-sharing).

## Изменённые файлы этой порции

Сравнение с рабочими файлами перед началом P4; ранее подготовленные файлы P3 перечислены в `second-batch-report.md`.

- `.gitignore`
- `app/src/androidTest/java/com/rslnabk/aivideotest/GenerationFlowTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/LibraryFlowTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/ResultExportTest.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/rslnabk/aivideotest/AppViewModel.kt`
- `app/src/main/java/com/rslnabk/aivideotest/MainActivity.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/demo/DemoSession.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/media/ExportController.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/media/ResultMedia.kt`
- `app/src/main/java/com/rslnabk/aivideotest/model/Export.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/catalog/BrowserFragment.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/common/DemoDialogs.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/generator/CreatingFragment.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/library/CreationDialogs.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/library/GenerationCard.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/result/ExportStatusDialog.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/result/ResultFragment.kt`
- `app/src/main/res/drawable-nodpi/demo_empty_favorites.webp`
- `app/src/main/res/drawable-nodpi/demo_empty_library.webp`
- `app/src/main/res/layout/fragment_result.xml`
- `app/src/main/res/values/ids.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/xml/file_paths.xml`
- `app/src/test/java/com/rslnabk/aivideotest/LibrarySessionTest.kt`
- `docs/screen-implementation-plan.md`
- `docs/third-batch-assets.json`
- `docs/third-batch-report.md`
- `tools/extract_library_assets.py`
