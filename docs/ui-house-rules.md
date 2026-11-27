# Design system — calm canvas, rich colour

The look is a calm, light canvas with white cards, ink-black actions, and soft
pastel gradients behind the numbers that matter (inspired by the Unfold expense
tracker). Dark mode follows the system and keeps the same roles.

Today (`ui/screens/today/`) is the reference implementation. Match it.

Light is the default; You → Appearance offers Light, Dark and Match system.

> Older code comments cite the previous "editorial" rules — black canvas,
> hairline-only sections, no fills, no emoji, accent reserved for data. Those
> rules are retired; where a comment disagrees with this page, this page wins.

## 1. Cards on a canvas

- The page is `background` (`#F4F4F7` light / `#0B0B0E` dark). Content sits in
  cards: `Section(title, trailing) { … }` for a titled card, `AppCard` for an
  untitled one. 24dp corners, `cardColor()` fill, no border, no shadow.
- Inside a sheet, cards switch to `AppExtras.cardInSheet` automatically
  (`AppSheet` / `AppModalSheet` provide it). Always use `AppModalSheet`, never
  Material's `ModalBottomSheet` directly.
- 12dp (`Spacing.m`) between cards on a page.
- Every section states its total in the same place: the `trailing` slot.

## 2. Type — pick a style by its job, never by its size

**Manrope** for headlines and figures, **Inter** for text — both Google
downloadable fonts (`ui/theme/Type.kt`), so they cost the APK nothing. No
`fontSize =` overrides in screens.

| Job | Style |
|---|---|
| The one hero figure on a screen | `numberXXL` (52) |
| A sheet's live readout | `numberXL` (36) |
| A figure in a card | `numberL` (24) |
| A figure in a dense row or grid | `numberM` (17) |
| Page title (tabs, onboarding) | `headline` (28) |
| Sheet title | `titleL` (20) |
| Card title, list item, pushed-page title | `titleM` (16) |
| Paragraphs, inputs | `body` (15) |
| Secondary lines, captions, units | `bodyS` (13) |
| Buttons | `button` (15) |
| Chips, tabs, pills | `label` (13) |
| Label above a figure or field | `eyebrow` (12) |

- Any "label over a number" uses `Metric(label, value, unit, size)` — so the
  same kind of number is always the same size.
- Numbers use comma grouping: `grouped(2468.0)` → `2,468`. Never format by hand.
- Animate hero figures with `AnimatedCount`. Numeric styles carry `tnum`.

## 3. Colour roles

| Role | Slot | Use |
|---|---|---|
| Ink | `primary` | Buttons, the "+" button, selected toggles/chips/tabs |
| Positive | `secondary` | On-track meters and status cells, "done" ticks |
| Accent | `tertiary` | The day on screen in charts, "Now", changed values |
| Over | `error`, `StatusOver` | Past a limit |
| Data | `Macro*` | One hue per nutrient, same everywhere |

- **Hero gradient**: `Modifier.meshGradient()` + `dotTexture()` — the soft
  pastel field behind a screen's one hero (Today's calories, the profile card,
  onboarding art). It takes a `parallax` scroll offset. At most one per screen.
- **Nutrient hues**: `Rich.*`, one per thing we measure — Energy (calories),
  Protein, Carbs, Fat, Fiber, Water, Weight, Micros, Streak. Each has a soft
  pastel `brush` and a deep `ink`. Colour goes on the **details, not the
  background**: a bento tile (`RichTile`) is a plain white card with the
  nutrient's `ink` on its icon, label and bar. Only the streak tile
  (`featured = true`) gets its soft gradient and dots. Pastel brushes are
  otherwise for small discs. Keep it subtle — nothing saturated behind text.
- **Dot texture**: `Modifier.dotTexture()` — only on gradient surfaces (the
  hero, the streak tile, onboarding art); it fades across the card.
- **Pastels** (`Pastel.*`) are for small icon discs and food tiles only.
- Small controls on any background: `softFill()` — it picks grey on white and
  white on grey so chips, steppers and toggle tracks always contrast.

## 4. Icons

Lucide (`com.composables:icons-lucide`) everywhere: `Lucide.Plus`,
`Lucide.ChevronRight`, … Put an icon on a `PastelIcon` disc when it labels a
row or card. Don't mix in Material icons (the filled favourite star is the one
exception). Food emoji stay in the food picker only.

## 5. Components

Use these; don't hand-roll a variant.

| Need | Component |
|---|---|
| Titled card / untitled card | `Section` / `AppCard` |
| Bento tile | `RichTile` (gradient) or a plain card |
| Label + figure | `Metric` |
| Primary / secondary / text action | `AppButton` / `AppOutlinedButton` / `AppTextButton` |
| Text input / search | `AppTextField` / `AppSearchField` |
| Number input | `NumberField`, `NumberStepper` (round), `HapticSlider` |
| On/off | `AppSwitch` |
| One-of-N, short labels | `ExclusiveButtonGroup` (sliding ink pill) |
| One-of-N, long labels | `OptionList` / `OptionRow` (full-width rows, check, no radio circle) |
| Plan vs actual over time | `PlanChart` (dashed plan, solid actual, shaded "ahead") |
| Chip | `HapticFilterChip` |
| Food in a list / grid | `FoodRow` / `FoodCard` (emoji from `FoodEntity.emoji()`) |
| Sheet | `AppSheet` / `AppModalSheet` |
| Icon disc | `PastelIcon`, `RoundIconButton` |
| Nothing here yet | `EmptyState` |

Small controls take `softFill()`, which picks grey on a white card and white
on grey — never hard-code a control's fill.

## 6. Navigation

- Three tabs — Today, Trends, You — in a floating **frosted-glass** pill bar
  (Haze backdrop blur), with a round ink "+" beside it that logs food from
  anywhere. The selected tab is an ink capsule that grows to show its label.
- Tab screens pad their scrolling content by `bottomBarClearance()`.
- Pushed pages use `AppTopBar(onBack = …)`: round back button, centred title.
  Tab screens use the large left-aligned headline.

## 7. Motion

Small and quick, never in the way:

- `Modifier.staggerIn(index)` — cards fade up on first show, 60ms apart, once.
- `Modifier.pressable { }` — tappable cards sink to 96% while pressed.
- Meters and chart bars grow from zero; toggles and chips animate their fill;
  the segmented pill slides.
- The hero's gradient and dots drift at a fraction of the scroll (parallax).
- Tab switches fade through; pushes slide.

## 8. Spacing and geometry

- Spacing: `4 · 8 · 12 · 16 · 20 · 24 · 32` via `Spacing.*`. Raw `.dp` is for
  sizes, not gaps.
- Radii: `Radius.sm` 10, `md` 16 (inputs, tiles), `lg` 24 (cards), `sheet` 28,
  `pill` for buttons, chips, toggles and the nav bar.

## 9. A control's size token is a minimum, never a height

`heightIn(min = Sizes.button)`, not `height(Sizes.button)`. The system font
scale runs from 0.85 to 2.0; a fixed height crops text rather than shrinking
it. Sheets pass `skipPartiallyExpanded = true` (the `AppModalSheet` default)
so a tall sheet cannot open with its footer off-screen.
