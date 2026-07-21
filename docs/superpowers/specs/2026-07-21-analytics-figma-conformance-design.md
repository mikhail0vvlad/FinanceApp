# Analytics — Figma conformance fixes

**Date:** 2026-07-21
**Branch:** `dz_2_rabota_s_setju`
**Scope:** Analytics screen + shared components, matched against the trimmed Figma copies
provided by the user.

## Figma sources

- Analytics screens + Детализация sheet: file `LawLhXCNvyeRp6TPhzy71C`
  (main screen `2001:4485`, detail overlay `2001:5039`).
- Analytics filter sheets ("Фильтры аналитики"): file `hgi9rdLAtoot8U5jjUK98s`
  (Тип `2041:4374`, Период `2041:4402`, Статьи `2041:4439`, Счёт `2041:4496`,
  **Произвольный период** `2041:4536`).

Read via the Figma MCP (view+dev access on the user-owned copies). Exact-token
inspection available; colors/typography confirmed against MCP variable defs.

## Confirmed matches (no change)

- Donut segment palette — app `ChartPalette[0..2]` = `#B69DF8 / #4DD8E6 / #F48FB1`
  matches the Figma purple/cyan/pink.
- Detail sheet (Детализация) structure: centered title, donut, breakdown rows with
  progress bars — matches.
- Filter labels: Тип / Период / Статьи / Счёт — strings already match.
- Period preset sheet rows (За неделю/месяц/квартал/год) — match.

## Discrepancies to fix

### 1. Custom-period picker (primary) — `AnalyticsSheets.CustomPeriodDialog`
- **Now:** Material `DatePickerDialog` wrapping a `DateRangePicker` capped at
  `heightIn(max = 480.dp)`. The range picker is designed for full-bleed use, so inside a
  small dialog its "Выберите даты" header + calendar are cramped and misaligned ("как попало").
- **Target (Figma `2041:4536`):** a `ModalBottomSheet` styled like the other filter sheets:
  - drag handle;
  - title **«Произвольный период»** (bold, left, sheet-title style);
  - a row of two outlined, read-only date fields: start «20 янв. 2026» — end «5 фев. 2026»;
  - inline **Monday-first** month calendar with purple range highlight (start/end filled
    circles, in-between light-purple band), months stacked vertically & scrollable;
  - bottom action row: **Отмена** (text button) + **Применить** (filled pill button).
- **Approach:** host a Material `DateRangePicker` inside a `ModalBottomSheet`, suppress its
  built-in `title`/`headline` (supply our own title + the two date fields as the headline),
  `showModeToggle = false`, and add the Отмена/Применить row. Monday-first comes from the ru
  locale. Keep the existing `onConfirm(start, end)` contract.

### 2. Back icon — `AnalyticsScreen.AnalyticsTopBar`
- **Now:** `Icons.AutoMirrored.Filled.ArrowBack` (solid ← arrow).
- **Target:** chevron-left (‹) — `Icons.AutoMirrored.Filled.KeyboardArrowLeft`.

### 3. Analytics top-bar title size / layout — `AnalyticsScreen.AnalyticsTopBar`
- **Now:** `headlineLarge` (34sp), padding `horizontal = 8, vertical = 12`.
- **Target:** Figma title box ~33px tall ≈ 24sp on a standard 64dp bar. Reduce toward
  ~24sp and align the bar to 64dp. **Verify on device before finalizing the exact value.**

### 4. Filter-row icon circle — `AnalyticsScreen.FilterRow`
- **Now:** 40dp circle + 20dp icon.
- **Target:** 32dp circle + 18dp icon (Figma "Icon Circle" 32, icon 18); row height 56dp.

### 5. Filter chip (badge) padding — `AnalyticsScreen.FilterRow`
- **Now:** `RoundedCornerShape(20dp)`, padding `14×8`.
- **Target:** ~24dp-tall pill, padding ~`12×4`. **Verify on device.**

### 6. Period-sheet selection mark — `AnalyticsSheets.SelectionMark`
- **Now:** `Icons.Filled.CheckCircle` (filled circle + check).
- **Target:** plain checkmark ✓ (Figma shows a simple check, purple). Minor.

## Verification

1. Build + install on the physical phone (Samsung SM-G780G, `RF8R9047A5E`).
2. Seed a test account + transactions via the test API (demo token has 0 accounts →
   otherwise every data screen is Empty and the donut/detail can't be seen).
3. Screenshot Analytics + each filter sheet + custom-period picker, before and after.
4. Only apply items 2–6 that the on-device baseline actually confirms as off; item 1 is
   confirmed independently of data.

## Out of scope

- Расходы / Доходы / Счета list screens beyond their shared components (TopBar, bottom nav,
  ListItem) which are checked opportunistically on-device.
