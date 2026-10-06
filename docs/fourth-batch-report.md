# Четвёртая порция: PRO / Tokens на Compose

Дата: 6 октября 2026. Закрыты P5 / A20–A22. Следующая порция P6 — онбординг, отзывы / разрешения и полные Settings.

## Что работает

- PRO: два тарифа, Year $29.99 и Week $9.99, выбранный тариф сохраняется при повороте. Карточки, преимущества, SAVE 40%, Continue, Restore Purchases, Terms и Privacy воспроизведены по PDF.
- Tokens: четыре пакета по 100 токенов за $19.99, бейджи у последних трёх. Баланс берётся из общего состояния аккаунта. Нажатие на пакет запускает демопокупку.
- Покупка и restore: Loading, Success, Cancelled, Failed, Retry; для restore также Nothing to restore. В процессе можно отменить операцию кнопкой или Back. Повторный запуск операции заблокирован до завершения / подтверждения текущей.
- Подписка меняет PRO и выбранный план; пакет добавляет токены. Restore восстанавливает последнюю локально купленную подписку и никогда повторно не начисляет расходуемые токены. Очистка данных приложения / Reset demo удаляет и демочеки.
- В Settings показан текущий Free / PRO / тариф; доступны PRO, Get tokens и Restore. Во всех заголовках обновляется тот же баланс. Полное оформление и остальные действия Settings остаются P6.
- При недостатке баланса Generate / Retry открывает Tokens с сохранённым назначением. После успеха Continue creation принимает одну задачу и списывает её стоимость один раз. Закрытие / отмена возвращает к прежнему вводу. Retry использует прежний immutable-запрос и ID задачи, даже если черновик изменён.
- После холодного запуска покупка завершается по сохранённому сроку. Продолжение восстанавливает нужный Prompt / Effect / Library; повторное подтверждение старой операции не создаёт новую задачу.
- Долгий тап по балансу открывает debug-панель. Добавлены Next purchase / restore: success / cancelled / error. Выбранный исход применяется один раз; при Retry по умолчанию используется успех.

UI целиком на Jetpack Compose / Material 3. Используются существующие DsCardSurface, DsButton, InfoDialog, палитра и типографические роли. Числа токенов дизайн-системы и зависимости не менялись. Системные picker, camera, экспорт и прежние сценарии сохранены.

## Сверка с PDF и неоднозначности

Источник: `157 - Higgsfield AI  (Vlad, 06.08.2025) (Copy) (Copy).pdf`. Просмотрены варианты на страницах 13–24, отдельно в увеличении — 13, 14, 19 и 24. Иллюстрация извлечена из самостоятельного растрового объекта страницы 13; статусная строка, тексты и кнопки не запечены в картинку. Происхождение и SHA256 — `fourth-batch-assets.json`.

| Неоднозначность | Принято для демо |
| --- | --- |
| На странице 14 первый тариф назван Week за $29.99, тогда как на 13 — Year | Year $29.99 и Week $9.99 по странице 13; переключение радио-кнопок меняет выбранный продукт |
| На всех четырёх карточках Tokens повторяются 100 / $19.99 | Сохранены буквально. Четыре внутренних fixture ID различают карточки; объёмы и реальные store SKU не придуманы |
| SAVE 40% не имеет объяснённой базы сравнения | Надпись сохранена из PDF; демо не рассчитывает скидку и не подтверждает её экономический смысл |
| Unlimited generation противоречит отдельным балансам и ценам генерации | По принятому плану PRO и токены независимы. PRO не пополняет баланс и не отменяет стоимость. Под исходными преимуществами есть явное пояснение деморежима |
| В PDF нет макетов платёжного Loading / Success / Error и правил restore | Добавлены локальные состояния и модальные подтверждения. Restore восстанавливает только PRO из локального журнала |
| Нет содержимого / адресов Terms и Privacy | Открывается пояснение деморежима. Реальные договоры / ссылки и платёжный SDK в этой порции не добавляются |
| Малые варианты 18 / 24 перекрывают иллюстрацию содержимым | Используется прокрутка. Иллюстрация движется вместе с содержимым; область Close зарезервирована. При крупном шрифте ссылки переносятся, бейджи Tokens выводятся отдельной строкой |

SF Pro остаётся системным Android fallback, как в предыдущих порциях: пригодного для встраивания файла шрифта не было. Числовые роли типографики сохранены. Android RadioButton и штатные диалоги адаптируют iOS-контролы PDF.

## Сохранение и совместимость

DemoSession остаётся источником состояния. В DemoSnapshot добавлен DemoCommerce с операцией и локальными чеками; DemoAccount хранит необязательный план. Старые tokens / pro / favorites / generation_v1 читаются без миграции и потери истории.

Новый commerce_v1 сохраняет стабильные имена продуктов / состояний и намерение продолжить генерацию. Android resource ID в нём нет. Начисление, чек и Success записываются одним снимком. Подтверждение продолжения, принятая задача и списание также сохраняются вместе. Переходы покупки используют синхронную запись SharedPreferences; редактирование черновика сохраняет прежний асинхронный путь. Завершённая операция повторно не начисляется при reconcile / повороте / новом процессе; устаревшие ID не могут подтвердить другую покупку.

## Проверки

- Успешны assembleDebug, testDebugUnitTest, connectedDebugAndroidTest и lintDebug.
- 24 unit tests: 17 прежних и 7 новых. Покрыты однократное начисление, конкурирующий запуск, отмена / ошибка / повтор, restore без повторного начисления пакета, независимость PRO и баланса, продолжение Generate / Retry, устаревшие callback и Reset.
- Полный прогон на Pixel 7 / Android 14 (API 34): 23 instrumentation tests без ошибок — 16 прежних и 7 новых. Проверены тариф / поворот / restore, четыре пакета в маршрутах покупки, отмена в Loading, error → Retry, закрытие offer, сохранённый Prompt, продолжение, холодный запуск и совместимость Preferences / JSON. После последних изменений разметки повторно прошли все 5 PurchaseFlowTest.
- Визуально сверены Year / Week, Tokens, компактный экран 320×640dp со шрифтом 150%, прокрутка до Continue / ссылок и диалог успеха. Исправлены наложение SAVE на цену, разрыв слова tokens, фон за прокрученными карточками и перекрытие действий кнопкой Close.
- Дополнительно приложение действительно остановлено в фазе Loading покупки PRO на выделенном эмуляторе и запущено вновь: статус восстановлен, PRO активирован, токены сохранены. Автотесты отдельно проверяют однократность токенов и продолжения при холодном запуске.
- Lint: 0 ошибок, 29 предупреждений, 1 hint; новые предупреждения о plural-строках устранены. Остались прежние замечания о версиях зависимостей, общих Compose-компонентах и неиспользуемых ресурсах.
- git diff --check успешен. Коммит в этой порции не создан. Изменения .idea/gradle.xml и .idea/misc.xml существовали до начала работы и не относятся к реализации.

Границы: реальных списаний, продления, истечения подписки, магазинных чеков, аккаунтов и сервера нет. Отказ физического накопителя во время записи не моделировался. API 24 / 37, landscape и полная матрица увеличенного шрифта для прежних экранов остаются P7. Для реального billing нужно отдельно определить entitlement / unlimited-политику, store-продукты, цены и договоры.

## Как проверить

1. Settings → More → выбрать Year / Week → Continue → Done. Баланс прежний, заголовок и Settings показывают PRO.
2. Баланс → выбрать пакет → Done: добавлено 100. До покупки долгим тапом по балансу выбрать исход error / cancelled для проверки соответствующих веток.
3. Free с 5 токенами → Prompt → заполнить запрос → Generate → Tokens → пакет → Continue creation. Создаётся один результат; возврат сохраняет ввод.
4. Settings → Restore: без локальной подписки показывается Nothing to restore. После покупки PRO и переключения debug на Free восстанавливается прежний план; баланс не пополняется.

## Изменённые файлы

Относительно коммита `04cdd42`, без ранее существовавших настроек IDE:

- `app/src/androidTest/java/com/rslnabk/aivideotest/GenerationFlowTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/PurchaseFlowTest.kt`
- `app/src/androidTest/java/com/rslnabk/aivideotest/PurchasePersistenceTest.kt`
- `app/src/main/java/com/rslnabk/aivideotest/AppViewModel.kt`
- `app/src/main/java/com/rslnabk/aivideotest/MainActivity.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/demo/CommerceJson.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/demo/DemoSession.kt`
- `app/src/main/java/com/rslnabk/aivideotest/data/demo/PreferencesDemoStore.kt`
- `app/src/main/java/com/rslnabk/aivideotest/model/Catalog.kt`
- `app/src/main/java/com/rslnabk/aivideotest/model/Purchase.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/catalog/BrowserScreen.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/common/AppDialogs.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/navigation/AiVideoApp.kt`
- `app/src/main/java/com/rslnabk/aivideotest/ui/offers/OfferScreen.kt`
- `app/src/main/res/drawable-nodpi/demo_offer_hero.webp`
- `app/src/main/res/values/offers_strings.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/rslnabk/aivideotest/PurchaseSessionTest.kt`
- `docs/design-system.md`
- `docs/fourth-batch-assets.json`
- `docs/fourth-batch-report.md`
- `docs/screen-implementation-plan.md`
- `tools/extract_offer_assets.py`
