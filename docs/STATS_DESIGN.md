# Stats tab: structure, design system and motion (v2, from scratch)

The first Stats UI (v0.12 to v0.14) is deleted. It was a dashboard: grey cards of equal weight. This replaces it.
The data layer (`StatsModel`, queries, `Insights`, tests) is kept; see `STATS_PLAN.md` for that.

## 1. What research said (condensed)

Sources: teardown of Wrapped, Replay, stats.fm, Last.fm, ListenBrainz, Pano; emotional-data design (Strava, Whoop, Oura, Apple, Duolingo); Android craft.

- Wrapped is **one number per screen**, the numeral itself is the graphic (huge, tight, repeated), text sits only on **flat colour fields**, motion is restrained (the team dialled it back in 2024).
- Replay wins on **trust** (live, accurate, archived), loses on emotion. Wrapped wins on emotion, lost trust in 2025 over unexplained numbers and gimmicks (Listening Age).
- stats.fm's complaints: bloat, slow loading, numbers that do not match. Local-first means **instant load** and **explainable counting** can be our headline advantages.
- Whoop: three tiers. Overview with no charts, one trend question per screen, raw deep dive on its own screen. Hero numeral is huge, supporting text small: size alone signals priority.
- Apple HIG: write the takeaway as a **sentence above the chart**. Few chart forms, all familiar.
- Duolingo: archetypes give everyone a reason to share, not only top users. Percentile on the card raised shares.
- Share cards: 9:16 and 4:5, key content in the centre safe band, hero 200px+ on 1080 canvas, body text 40px+, flat colour behind text, small brand mark.
- Android: 20dp gutters, 28dp card radius, tonal layers not shadows on dark, 48dp touch targets, springs for movement and tweens for colour, count-up once per load, haptic ticks on scrub, reduced-motion and TalkBack summaries for charts.

## 2. Decisions

1. **Two modes.** A live **Stats dashboard** (this doc) and, later, a **Recap story** (week, month, year) built from one template. Posters are *exports*, not scroll content.
2. **Our signature: the yellow label.** The hero is a flat Tape Yellow card with giant black numerals, like a cassette label. Flat field, maximum contrast, instantly Oto, and it uses the single accent allowed per viewport. No gradients behind numbers.
3. **Cover-art colour lives only in exports and thin tints.** In the app, covers carry the colour. Posters use cover-derived colour on a flat field.
4. **Honest personal superlatives instead of fake percentiles.** We have no population, so "top 1%" would be a lie. We compare you with *you*: "Your 2nd biggest month of 14", "Longest streak since March".
5. **Explainable labels.** Every personality label shows the rule behind it ("Night Owl: 41% of listening after midnight"). No predictions (no "Listening Age").
6. **Trust line.** A quiet footer: "Counted from 30 seconds or 50% of a track. Tracking since Mar 3, 2025." Visible counting rule.
7. **Six to eight modules, then detail screens.** Anything more goes behind "See all".
8. **One list at a time.** Artists / Tracks / Albums share one card with a segmented control instead of three stacked lists.

## 3. Structure (top to bottom)

| # | Module | Question it answers | Chart form |
|---|--------|---------------------|-----------|
| 0 | Header (glass) | Where am I | none |
| 1 | **Period control** | Which window | segmented capsule, title, chevrons, dates |
| 2 | **Hero (yellow label)** | How much did I listen | giant numeral + one sentence + delta chip |
| 3 | **Your days** | How did it spread | rounded bars, peak lit, scrub to read |
| 4 | **Most played** | What did I play most | one ranked list (segmented), #1 featured with cover, proportional bars |
| 5 | **Your hour** | When do I listen | 24-bar clock dial, peak in yellow |
| 6 | **Showing up** | How consistent am I | calendar heat (month grid / year mosaic) |
| 7 | **How you listen** | Who am I as a listener | archetype with its rule, plus played-through / skips / replays / streak tiles |
| 8 | **Make a poster** | Share | opens the poster picker sheet |
| 9 | Trust line | Can I trust this | one caption |

States: no history, empty period, low data (hide 5, 6 and the rule line), loading skeleton in the shape of the real cards (data is local so this is a single frame).

Detail screens (after this): Artist, Track, Album, "See all" ranked lists, period archive.

## 4. Design system for Stats

**Grid.** 4dp base. Screen gutter 20dp. Card gap 12dp. Section gap 32dp. Card padding 20dp.
**Radii.** Cards 28dp. Inner tiles 16dp. Chips and bars 12dp or full. Bars 6dp top corners.
**Surfaces.** Canvas #111111, card #1A1A1A, raised #222222, hairline 1dp at 8% white. No shadows.
**Accent budget.** Tape Yellow only for: the hero card, the selected segment, the peak bar/hour, and the #1 row. Never two big yellow fields in one viewport (hero scrolls away before the next).
**Type.**
- Hero numeral: Bricolage Grotesque 700, 112sp to 128sp, tracking -3%, proportional figures. Unit at 28sp, baseline-aligned.
- Section title: Bricolage 24sp 600.
- Sentence under hero: Plex Sans 18sp 500.
- Labels: Plex Sans 12sp 500, caps, +8% tracking.
- Numbers in lists and tiles: Plex Sans tabular 14 to 20sp.
**Text colour.** Primary #F2F2F0, secondary #B0B0AD (above 4.5:1 on cards), on yellow: #111111.
**Touch.** 48dp minimum; chart bars get a wide invisible hit area.

## 5. Component specs

**Period control.** 44dp capsule, 3dp inner padding, sliding yellow pill (spring, no overshoot into layout), labels Week Month Year All. Below: period title (24sp), dates (12sp, tertiary), 44dp chevrons. Haptic: SegmentTick on change.

**Hero.** 28dp radius yellow card, 24dp padding. Top: label "LISTENING TIME" (black 60%). Numeral: hours huge + minutes at 56sp. Sentence: "3h 12m more than October. Your 2nd biggest month of 14." Delta chip: black 12% fill pill with arrow and percent. Count-up 1200ms once per load, strong ease-out. Right edge: a tilted cover print of your #1 track peeking out (links the hero to your music).

**Your days.** Header sentence above the chart ("Your busiest day was Sat 8, 5h 12m"). Bars 12dp to 16dp wide when few, 3dp to 6dp when dense; selected = yellow, others #2B2B2B, zero = 4% white outline stub; future/before-tracking drawn as dotted baseline. Scrub: drag reads a bar, SegmentFrequentTick only on index change. Entrance: bars spring from baseline, 30ms stagger.

**Most played.** Card with segmented Artists | Tracks | Albums and a Time | Plays toggle. #1 row: 72dp cover (artists: monogram disc), name in Bricolage 22sp, yellow proportional line. Rows 2 to 5: 48dp cover, 14sp, grey line. "See all" row. Values right-aligned, tabular.

**Your hour.** Sentence: "Mostly evenings, peaking at 9 PM." 24 radial bars around a ring; peak yellow, rest white 30%. Centre: peak hour. Under it: four daypart shares as a quiet row.

**Showing up.** Month: 7-column calendar, cells shaded by intensity (yellow at 4 steps), day numbers inside. Year/All: 12 rows x 31 columns mosaic. Caption: "24 of 30 days. Longest day Sat 8. Streak 12."

**How you listen.** Archetype name in Bricolage 28sp with the rule beneath it ("41% of your listening happens after midnight"). Below: four tiles (Played through, Skipped, Replays, Streak) with small rings.

**Poster picker (sheet).** Horizontal pager of the poster set (Time, Artist, On repeat, Hour, Days, Discovery). Format toggle 9:16 / 4:5. Share button confirms with haptic. Exports are rendered from a dedicated layout at 1080 wide, not a screenshot of the screen. Flat colour field from cover art, giant numeral 200px+, small "oto" mark bottom-left in the safe band.

## 6. Motion and haptics

- Cards: translateY 24dp to 0, alpha 0 to 1, 350ms emphasised decelerate, 50ms stagger, total at most 400ms. Not replayed on tab revisit; once per data load.
- Count-up: 1200ms hero, 900ms others, 150ms delay, strong ease-out, tabular or fixed-width digits so width never jitters.
- Bars: spring (damping 0.75, stiffness 300), 30ms stagger, overshoot under 4%.
- Press: scale 0.97 spring (0.6, 800), release spring (0.5, 400).
- Colour and alpha: no overshoot, 100 to 200ms.
- Period switch: content cross-fades 180ms, numbers re-count.
- Haptics: SegmentTick on segment change, SegmentFrequentTick on scrub index change, Confirm on share.
- Reduced motion (animator scale 0): jump to final states.

## 7. Accessibility

Each chart container carries a spoken summary ("Listening time this month. 41 hours, up 12% on October. Busiest day Saturday the 8th, 5 hours 12 minutes."). Decorative glows and grain are hidden from TalkBack. Stat cards merge label and value. Test at 2.0x font scale; hero numerals shrink to fit instead of wrapping.

## 8. Architecture

- Keep: `StatsPeriod`, `StatsMath`, `Insights`, `ActivityBuckets`, `StatsModel` (flatMapLatest, debounce), DAO queries, `ArtPalette`, `ShareImage`.
- Add: `PeriodRank` (own-history superlatives from all-time daily totals; pure and tested), a `StatsUiState` split into small immutable section models so each module recomposes alone.
- UI packages: `ui/stats` (screen and modules), `ui/stats/charts` (bars, dial, calendar, mosaic), `ui/poster` (export layouts and picker).
- Motion tokens in one object (`OtoMotion`), spacing tokens in one object (`OtoSpace`), so the spec above is code, not folklore.

## 9. Build order

1. Tokens (`OtoSpace`, `OtoMotion`) and `PeriodRank` with tests.
2. Shell of the screen: period control, hero, trust line, all states.
3. Charts: days, hour dial, calendar and mosaic.
4. Most played card.
5. How you listen.
6. Poster picker and 1080-wide exports.
7. Detail screens.
