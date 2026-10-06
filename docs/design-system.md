# Дизайн-система AiVideoTest

## Источники и подход

Источник истины: прикреплённый PDF `(2)` (20 страниц), страница 19 — типографика, страница 20 — палитра; PDF `(1)` (49 страниц) — компоненты и состояния. Первые 18 страниц PDF `(2)` — отдельные изображения/иконки, не дополнительные токены.

Текущий UI: Jetpack Compose / Material 3, ComponentActivity и Navigation Compose. XML-экраны, Fragment и ViewBinding удалены. `ui/theme/AiVideoTheme.kt` читает 28 цветов из `design_colors.xml` и 34 типографические роли из `typography_dimens.xml`, без дублирования численных значений. Экраны используют `Ds.colors` и `Ds.type`. XML-тема оформляет только системное окно. Подробности миграции — в `compose-migration-report.md`.

## Цвета

Все 28 значений сохранены. Имена `/` и camelCase преобразованы в snake_case с префиксом ds_. В PDF восьмизначный цвет — RRGGBBAA; в Android — AARRGGBB. Например, #d1fe171f становится #1FD1FE17. Прозрачность не заменяется заранее смешанным цветом.

| PDF | Android | PDF RGBA | Android ARGB/RGB |
| --- | --- | --- | --- |
| accent/primary | `ds_accent_primary` | `#d1fe17` | `#D1FE17` |
| accent/primaryAlpha | `ds_accent_primary_alpha` | `#d1fe171f` | `#1FD1FE17` |
| accent/secondary | `ds_accent_secondary` | `#a9fe17` | `#A9FE17` |
| accent/grey | `ds_accent_grey` | `#b4b2b01f` | `#1FB4B2B0` |
| accent/green | `ds_accent_green` | `#15cc68` | `#15CC68` |
| accent/red | `ds_accent_red` | `#ff5446` | `#FF5446` |
| accent/Pink | `ds_accent_pink` | `#fb7acd` | `#FB7ACD` |
| accent/Yellow | `ds_accent_yellow` | `#fdb03c` | `#FDB03C` |
| label/primary | `ds_label_primary` | `#faf5ff` | `#FAF5FF` |
| label/primaryInvariably | `ds_label_primary_invariably` | `#faf5ff8c` | `#8CFAF5FF` |
| label/primaryInverted | `ds_label_primary_inverted` | `#22242c` | `#22242C` |
| label/primaryInvertedInvariably | `ds_label_primary_inverted_invariably` | `#22242c8c` | `#8C22242C` |
| label/secondary | `ds_label_secondary` | `#faf5ffcc` | `#CCFAF5FF` |
| label/tertiary | `ds_label_tertiary` | `#faf5ff99` | `#99FAF5FF` |
| label/quaternary | `ds_label_quaternary` | `#faf5ff66` | `#66FAF5FF` |
| label/quintuple | `ds_label_quintuple` | `#faf5ff47` | `#47FAF5FF` |
| label/tertiary 2 | `ds_label_tertiary_2` | `#faf5ff99` | `#99FAF5FF` |
| background/primary | `ds_background_primary` | `#0d0d0d` | `#0D0D0D` |
| background/primaryAlpha | `ds_background_primary_alpha` | `#0d0d0df0` | `#F00D0D0D` |
| background/secondary | `ds_background_secondary` | `#232323` | `#232323` |
| background/tertiary | `ds_background_tertiary` | `#faf5ff14` | `#14FAF5FF` |
| background/quaternary | `ds_background_quaternary` | `#faf5ff24` | `#24FAF5FF` |
| background/dim | `ds_background_dim` | `#faf5ff66` | `#66FAF5FF` |
| separator/primary | `ds_separator_primary` | `#2e2e2d` | `#2E2E2D` |
| separator/secondary | `ds_separator_secondary` | `#5759508c` | `#8C575950` |
| system/neutral | `ds_system_neutral` | `#ffffff` | `#FFFFFF` |
| system/white | `ds_system_white` | `#ffffff` | `#FFFFFF` |
| system/black | `ds_system_black` | `#000111` | `#000111` |

## Типографика

SF Pro — семейство в исходнике. Файлов шрифта, пригодных для Android, не приложено. SF Pro не встроен и не извлечён из PDF. Сейчас используется системный sans-serif (обычно Roboto); метрики и числовые веса исходника сохранены как токены, но форма глифов будет отличаться. Ограничения лицензии Apple: https://developer.apple.com/fonts/ (раздел License Agreement, 2A–2B). Для точного семейства необходим шрифт с правом встраивания в Android или утверждённая замена.

В Compose fallback централизован в AiVideoTheme: FontFamily.SansSerif, числовые веса 400/500/600/700 и FontStyle.Italic. Отрисовка веса зависит от системного шрифта устройства. При подключении лицензированного семейства заменить FontFamily в этом месте, сохранив роли.

Размеры px из дизайн-макета перенесены численно в sp, высота строки тоже в sp; геометрия компонентов — в dp. Compose использует TextStyle с includeFontPadding=false и letterSpacing=0. Исходные sp считываются без предварительного масштабирования, поэтому пользовательский размер шрифта применяется один раз.

| Стиль | Вариант | Размер/строка (sp) | Вес |
| --- | --- | --- | --- |
| LargeTitle | Regular | 34/41 | 400 |
| LargeTitle | Emphasized | 32/34 | 600 |
| Title1 | Regular | 28/34 | 400 |
| Title1 | Emphasized | 28/34 | 700 |
| Title2 | Regular | 22/28 | 400 |
| Title2 | Emphasized | 22/28 | 700 |
| Title3 | Regular | 20/25 | 400 |
| Title3 | Emphasized | 20/25 | 600 |
| Headline | Regular | 16/20 | 400 |
| Headline | Emphasized | 16/20 | 600 |
| Body | Regular | 12/16 | 400 |
| Body | Emphasized | 15/18 | 500 |
| Body | Italic | 17/22 | 400 |
| Body | EmphasizedItalic | 17/22 | 600 |
| Callout | Regular | 16/21 | 400 |
| Callout | Emphasized | 18/22 | 600 |
| Callout | Italic | 16/21 | 400 |
| Callout | EmphasizedItalic | 16/21 | 600 |
| Subheadline | Regular | 15/20 | 400 |
| Subheadline | Emphasized | 15/20 | 600 |
| Subheadline | Italic | 15/20 | 400 |
| Subheadline | EmphasizedItalic | 15/20 | 600 |
| Footnote | Regular | 10/12 | 400 |
| Footnote | Emphasized | 13/18 | 600 |
| Footnote | Italic | 13/18 | 400 |
| Footnote | EmphasizedItalic | 13/18 | 600 |
| Caption1 | Regular | 12/16 | 400 |
| Caption1 | Emphasized | 12/16 | 500 |
| Caption1 | Italic | 12/16 | 400 |
| Caption1 | EmphasizedItalic | 12/16 | 500 |
| Caption2 | Regular | 11/13 | 400 |
| Caption2 | Emphasized | 11/13 | 600 |
| Caption2 | Italic | 11/13 | 400 |
| Caption2 | EmphasizedItalic | 11/13 | 600 |

Имена стилей в Compose: `Ds.type.largeTitleRegular`, `Ds.type.title1Emphasized`, `Ds.type.bodyEmphasizedItalic` и т. д. Стандартные роли Material 3 сопоставлены этим стилям в теме; это интеграционные алиасы, а не новые значения из PDF.

## Компоненты Compose

`DsButton`, `RoundAction`, `ScreenHeader`, `Segmented`, `InfoDialog`, `BalanceButton`, `EffectCard`, `GenerationCard`, `PromptEditor` и Compose ModalBottomSheet используют семантическую палитру. Высоты кнопок 56/44dp и радиус поля запроса читаются из component_dimens.xml. Disabled Generate сохраняет background/secondary и label/quaternary из второй порции. Динамическая палитра отключена, чтобы сохранить цвета PDF. DsCardSurface и DsSearchField заменяют соответствующие базовые XML-стили; примеры доступны в DesignSystemPreview. PRO / Tokens используют DsCardSurface для тарифов и пакетов, DsButton для Continue, семантические цвета и роли типографики. OfferScreen добавляет выбранный тариф, SAVE 40%, реальный demo-баланс и состояния покупок; числа палитры / типографики не менялись.

```kotlin
AiVideoTheme {
    Text("Title", style = Ds.type.title1Emphasized, color = Ds.colors.labelPrimary)
    DsButton("Continue", primary = true, onClick = {})
}
```

## Неоднозначности и ограничения

1. В PDF есть только dark-палитра. Она применяется при обоих режимах системы; light-цвета не выдуманы. Compose использует фиксированную палитру из PDF.
2. LargeTitle Emphasized — 32/34 (Regular 34/41); Body Regular — 12/16, Emphasized — 15/18 Medium, italic-варианты — 17/22; Callout Emphasized — 18/22 (остальные 16/21); Footnote Regular — 10/12 (остальные 13/18). Они выглядят необычно, но перенесены дословно, без замены стандартными iOS-метриками.
3. Второй вариант Headline подписан «Headline», а не «Emphasized». В коде он Emphasized, потому что PDF указывает Semibold 16/20.
4. label/tertiary и label/tertiary 2 совпадают (#faf5ff99), system/neutral и system/white совпадают. Оба имени сохранены отдельно. system/black = #000111, не чистый #000000.
5. background/dim = #faf5ff66 — светлый прозрачный токен, не чёрная затемняющая маска. Сохранён буквально.
6. SF Pro отличается от фактического Android fallback. Числовой вес 600 сохраняется в TextStyle, но его отрисовка зависит от системного шрифта. При замене шрифта нужна проверка кириллицы, переносов и увеличенного текста.
7. PDF компонентов не экспортирует все имена, размеры, отступы и переходы состояний. Использованы измеренные контуры там, где это возможно; Android padding 16dp, stroke 1/2dp, ripple, disabled и связь компонентов с ролями — явно выбранные адаптации, не токены PDF. Серая заливка кнопок в PDF выглядит как отдельный #4d4d4d, которого нет в таблице; используется опубликованный accent/grey, различие требует уточнения дизайна.
8. SF Symbols из PDF не перенесены как текстовые private-use символы: без соответствующего шрифта на Android они дадут пустые квадраты. Векторные иконки и полноэкранные композиции нужно согласовать при добавлении экранов.
9. 32–44dp варианты кнопок воспроизводят визуальные размеры PDF; экранам следует обеспечить Android touch target отдельно. Результаты проверки Compose-экранов на эмуляторе — в отчёте миграции.
