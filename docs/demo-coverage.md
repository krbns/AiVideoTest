# Реестр покрытия demo AiVideoTest

Дата: 6 октября 2026. UI — Jetpack Compose. Нумерация ниже соответствует физическим страницам исходного PDF, а S01–S16 — семействам из `screen-implementation-plan.md`. Все 340 страниц классифицированы в `pdf-screen-inventory.md`: 164 страницы UI/состояний, 24 страницы компонентов, 4 иллюстрации, 20 условий и 128 служебных объектов. Повторные макеты и стрелки не превращены в отдельные destinations.

«Реализовано» означает доступный локальный демонстрационный сценарий. Это не подтверждение коммерческих правил, настоящих покупок, серверной генерации или пиксельного совпадения с iOS. Источники и неоднозначности сохраняются в отчётах порций 1–6 и отчёте P7 `sixth-batch-report.md`.

| ID / PDF | Реализация Compose | Покрытые состояния и действия | Автоматические проверки |
| --- | --- | --- | --- |
| S01 / 5 | `LaunchScreen` | Splash; новый/завершённый intro; без повторного offer | `PreferencesFlowTest`, `PreferencesPersistenceTest` |
| S02 / 6–12, assets 1–4 | `OnboardingScreen` | Welcome/Prompt/Share/Reviews/Photos/Notifications; Back, sample/picker/cancel; opt-in/skip; сохранение шага | `PreferencesFlowTest`, `PreferencesSessionTest`, `NotificationFlowTest` |
| S03 / 25–26, 336 | `RatingScreen`, `ReviewScreen` | Yes/No/Back; звёзды; имя/отзыв; лимит Unicode; сохранение локального ответа | `PreferencesFlowTest`, `PreferencesPersistenceTest`, `PreferencesSessionTest` |
| S04 / 13–18 | `OfferScreen(PRO)`, `PurchaseStatus` | Year/Week; close; loading/success/cancel/error/retry; restore/empty; продолжение запроса | `PurchaseFlowTest`, `PurchasePersistenceTest`, `PurchaseSessionTest` |
| S05 / 19–24 | `OfferScreen(TOKENS)`, `PurchaseStatus` | Четыре пакета; баланс; insufficient gate; success/cancel/error/retry; однократное пополнение | Те же purchase tests; `GenerationFlowTest` |
| S06 / 43–44, 67–68, 76, 152, 156, 161–162, 211 | `BrowserScreen` | Video/Photo Trends; баннер; секции; горизонтальная прокрутка; like; пять вкладок | `CatalogNavigationTest`, `DemoSessionTest`, `AdaptiveTabBarTest` |
| S07 / 117, 207 | `BrowserScreen(category)` | See all; пять категорий; сетка; Like; Back/origin/scroll | `CatalogNavigationTest` |
| S08 / 48–52, 59, 74, 79–94, 157–180 | `PromptEditor` | Video/Photo; 0/1/299/300/301 code points; пробелы; copy/clear; reference; 720/1080; пять стилей; восстановление | `GenerationSessionTest`, `GenerationFlowTest`, `DemoMatrixTest`, `PromptLayoutTest` |
| S09 / 118, 125, 134–135, 143, 208, 218, 225–226, 230, 246 | `EffectScreen` | Фото/статичный preview видео; like/unlike; Use this effect; вход из каталогов и Favorites | `CatalogNavigationTest`, `GenerationFlowTest`, `DemoMatrixTest` |
| S10 / 64, 119, 136, 209, 227, 247 | `AppDialogs(instruction)` | Good/Bad examples; Continue; cancel; первый/повторный вход | `GenerationFlowTest`, `DemoMatrixTest` |
| S11 / 54, 56, 71, 120–121, 130, 137, 139, 145, 210, 216, 219, 228, 232, 234, 249, 252, 280 | `AppDialogs(source/samples)`, platform Activity Results | Camera/gallery/sample/cancel; loading/failure/retry; remove/replace; нормализация/EXIF; один reference | `GenerationFlowTest`, `GenerationSessionTest`, `PhotoThumbnailTest`; системные окна — ручная проверка |
| S12 / 97, 186, 239, 258 | `CreatingScreen` | Running/Failed/Succeeded; Okay → Library; уход и завершение; retry; refund один раз | `GenerationFlowTest`, `GenerationPersistenceTest`, `LibrarySessionTest`, `DemoMatrixTest` |
| S13 / 99–115, 122–123, 188–205, 212–213, 260–278, 283–284, 290, 300–324 | `ResultScreen`, `DemoVideo`, export dialogs | Image/video; play/pause/background; menu; gallery/files/share; success/failure/retry/cancel; delete/cancel | `ResultExportTest`, `LibraryFlowTest`, `DemoMatrixTest` |
| S14 / 240, 242–244, 279, 282, 285 | `BrowserScreen(FAVORITES)` | Photos/Videos; empty/content; синхронный like/unlike; переход к эффекту; сохранение | `CatalogNavigationTest`, `LibraryFlowTest`, `DemoSessionTest` |
| S15 / 241, 286–288, 291, 293, 295, 297–298, 304, 306 | `BrowserScreen(LIBRARY)`, `GenerationCard` | Photos/Videos; empty/running/failed/ready; immutable retry; delete/cancel/last empty; открытие Result | `LibraryFlowTest`, `LibrarySessionTest`, `DemoMatrixTest` |
| S16 / 326, 328, 334–337, 339–340 | `SettingsScreen`, `MessageScreen`, shared dialogs | Free/PRO/Low tokens; More; notifications/preference vs OS; share/rate/report/restore/cache/letter/privacy/terms/version | `PreferencesFlowTest`, `PreferencesSessionTest`, `PreviewCacheTest`, `NotificationFlowTest` |

Финальная проверка P7: 46 device tests на каждом из API 34 и 36, 33 unit tests; везде 0 failures/errors/skipped. Native API 34: 390×844dp, 320×640dp при 200%, горизонтальный viewport и настоящий Android rotation с IME. Дополнительная адаптация Prompt и отчёты находятся в рабочем дереве после `76cdc5a`.

## Сквозная матрица

| Группа | Как проверяется | Граница доказательства |
| --- | --- | --- |
| Четыре входа в генерацию | `DemoMatrixTest`: Video Prompt 1080, Photo Prompt Fantasy, Video Effect, Photo Effect → Creating → Result → Library; recreate; одно списание | Медиа и задержка локальные; не проверка сервера |
| Баланс/покупки/restore | Unit + UI + JSON persistence: zero/insufficient/sufficient, Free/PRO, cancel/error/retry, continuation ровно один раз | PRO и токены независимы; цены/пакеты — demo из PDF |
| Навигация/избранное | UI: tabs, categories, filters, scroll, back/origin, like/unlike между каталогом/Effect/Favorites | Navigation сохранена при recreate; при новом обычном запуске старт AI Video, черновики/данные сохранены |
| Фото | Импорт actual file; EXIF/ограничение размера в importer; cancel/retry/remove/replace; native camera/pickers вручную | Сторонние/облачные providers и гибель процесса внутри камеры требуют отдельной проверки |
| Результат/экспорт | Actual JPEG/MP4; MIME/ClipData/read grants; rollback pending gallery; export error/retry; picker cancel/recreate; interrupted journal | Получатель не выбирается; внешняя отправка не выполняется. Interrupted Files сообщает ошибку; частичный внешний документ может остаться |
| Settings/уведомления | Preference/OS отдельно; opt-in/opt-out; ready claim один раз; cold notification intent; cache не удаляет originals/history/exports; все ссылки доступны | Уведомление о генерации приходит при работающем приложении либо после запуска; server push нет |
| Lifecycle | Recreate на Prompt/Effect/Creating/Result/Offers/forms; persisted JSON; реальная остановка процесса вручную | Не гарантируется текущая UI-route после force-stop; сохраняются долговременные demo-данные |
| Крупный шрифт | `AdaptiveTabBarTest` и `PromptLayoutTest`: 320dp, 150%/200%; целые подписи, доступные 48dp tab targets, counter/copy/clear; полный заголовок; Prompt viewport 90dp | Текст не уменьшается и font scale не отключается; панель вкладок может занимать несколько строк |
| Playback/память | `DemoMatrixTest`: pause/recreate/background/resume; `PhotoThumbnailTest`: actual bitmap, минимум 16× меньше allocation на fixture | Эмулятор не заменяет измерения на физическом устройстве; показатели FPS не заявлены |

## Открытые вопросы источника

- SF Pro не приложен как лицензированный шрифт Android: действует центральный SansSerif fallback; все 28 цветов и 34 числовые типографические роли сохранены.
- В PDF разный баланс/цены, названия Year/Week и одинаковые пакеты; unlimited PRO конфликтует с отдельными токенами. В demo правила явные; реальные entitlement/SKU/учёт надо согласовать отдельно.
- Favorites трактуется как эффекты, не результаты. Retry использует снимок исходного запроса, возвращает стоимость failed один раз, затем списывает при принятом retry.
- Один reference вместо неоднозначного picker «up to 4». Нужен продуктовый ответ перед изменением лимита.
- Video в Effect — still preview. Scroll Down не показывается для одного кадра. Result содержит короткий локальный тестовый clip, не настоящую генерацию/motion из PDF.
- iOS permission/rating/share заменены Android системными контрактами либо явным demo-экраном отзыва. Terms/Privacy, Report и Letter без предоставленных URL/содержимого остаются явно обозначенными локальными demo-формами/информацией.
- minSdk 24 и targetSdk 37 остаются в проекте. Runtime-проверки API 24–28 и 37, физического телефона, облачных document providers и аварии накопителя не считаются выполненными по сборке/lint.

Реестр закрывает реализацию семейств и доступные demo-пути; перечисленные адаптации не объявляются утверждёнными коммерческими правилами или проверенными production-интеграциями.
