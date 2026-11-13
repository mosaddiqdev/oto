# Stats tab: structure, rules and build order

Single source of truth for the Stats tab. It replaces the earlier plan and design docs.
The data layer is kept: `StatsPeriod`, `StatsMath`, `ActivityBuckets`, `Insights`, `PeriodRank`, `StatsModel`, the DAO queries and their tests. All Stats cards were deleted in 0.15.2 and are rebuilt one at a time, in the order below.

## 1. What the tab is for

Now answers "what is playing". **Stats answers "what is my listening like over a stretch of time, and how does it compare to before".** It is the reflective tab: you open it to look back. Every number comes from `listening_events`, the window is always visible, and one window drives every card.

## 2. What the research says

- **Summary first, detail on tap.** Whoop: three big numbers and no charts, then a trend screen per number, then raw graphs. "Each tile is a doorway, not a destination."
- **Comparison is the most repeated request** (stats.fm: compare time slots, compare the past, compare time of day; Last.fm: compare a month with the same month years ago).
- **Personal records are wanted** (most plays in one day, the month you played an artist most, a records tab).
- Also asked for: time of day, how much new music you played, any time window instead of one year.
- Wrapped's weakness: one obsession can dominate a single year. Replay's: basics only. Both are fixed snapshots, not ongoing history.
- Trust: people distrust numbers they cannot verify. Show the window, the counting rule and the tracking start date.
- Android craft: 20dp gutters, 28dp card radius, tonal layers rather than shadows, 48dp touch targets, count-up once per load, haptic ticks on scrub, reduced-motion and TalkBack summaries for charts.

## 3. The rule: every card is a doorway

The Stats screen is the **overview**. It never tries to show everything.

1. **Overview card**: one number or one chart, one takeaway sentence. No more than that.
2. **Doorway**: the whole card is tappable (48dp minimum, press scale 0.97, a small chevron at the card's top right) and opens its own full screen.
3. **Detail screen**: the full version of that question, with its own back button and the same window selection carried in.
4. **Deep dive**: raw numbers and breakdowns, reached from the detail screen only.

| Card | Opens |
|---|---|
| Hero | Period archive: every month or week with its total, to compare any two |
| Most played | Full ranked list (Artists / Tracks / Albums), then Artist / Track / Album detail |
| Your days | Day-by-day list, tap a day to see what played |
| Discovery | New tracks and new artists this period |
| Your hour | Hour by weekday heatmap |
| Showing up | Year view and streak history |
| Records | All records, each with its date |
| How you listen | The rule behind the label, and the numbers behind each tile |
| Poster | Poster picker |

## 4. Order of the screen, top to bottom

| # | Block | The question it answers | Why here |
|---|---|---|---|
| 1 | **Period control** (lives in the glass header, see section 6) | Which window? | Every number depends on it |
| 2 | **Hero**: listening time, comparison bars, one sentence, small share icon | How much did I listen, and is that more than usual? | The headline and the comparison people ask for |
| 3 | **Most played** (Artists / Tracks / Albums) | What did I play most? | Table stakes in every music stats app |
| 4 | **Your days** | How did it spread? | Explains the hero number |
| 5 | **Discovery**: new tracks, new artists, replays | Did I find new music or loop old? | Valued by Last.fm users |
| 6 | **Your hour** | When do I listen? | Time of day is a repeated request |
| 7 | **Showing up**: calendar and streak | How consistent am I? | Habit |
| 8 | **Records**: longest streak, biggest day, most-played day | What are my bests? | Requested on stats.fm |
| 9 | **How you listen**: label and its rule | Who am I as a listener? | Fun and shareable, after the useful parts |
| 10 | **Make a poster** | Can I share this? | Sharing is the end of the story |
| 11 | **Trust line** | Can I trust this? | One quiet line |

States: no history, empty period, low data (hide Your hour, Showing up, Records and the label rule until about 3 days and 10 plays), loading (data is local, so one frame).

## 5. Design rules for every card

- **Grid:** 4dp base, 20dp gutter, 12dp card gap, 32dp section gap, 20dp to 24dp card padding.
- **Radii:** cards 28dp, inner tiles 16dp, bars 6dp to full.
- **Surfaces:** canvas #111111, card #1A1A1A, raised #222222, hairline 8% white. No shadows.
- **Colour:** monochrome plus one accent, Tape Yellow #EFD43B. Yellow is used as light, not paint: lit gradient numerals, one peak bar, one selected segment. No large flat yellow fields. Semantic colours only for status.
- **Type:** Bricolage Grotesque for display numerals and titles, IBM Plex Sans for body, tabular figures in lists. Numerals are sized from the width available and never take more than about half a card.
- **Text:** primary #F2F2F0, secondary #B0B0AD, tertiary #848482.
- **Takeaway sentence above or below each chart.** One number per card.
- **Own-history superlatives, never fake percentiles.** "Your 2nd biggest month of 14."
- **Explainable labels.** Every personality label shows its rule.

## 6. Period control and the glass header (next task)

The Week / Month / Year / All pill belongs in the glass header that appears as you scroll, so the window stays visible. Design questions to settle:

- Resting state: pill sits in the page under the title.
- Scrolled state: the pill moves up into the glass bar (shared element feel: same pill, it does not duplicate).
- Period title and chevrons: where they live in the compact state.
- Must work with the existing `headerProgress` value that drives the glass header on all tabs.

## 7. Motion and haptics

- Cards enter once per data load: translateY 24dp to 0, fade, 350ms decelerate, 50ms stagger, 400ms total at most.
- Count-up 1200ms hero, 900ms others, strong ease-out, once per load.
- Bars spring (damping 0.75, stiffness 300), 30ms stagger. Clamp spring fractions to 0..1.
- Press scale 0.97 down, spring back. Colour and alpha changes 100ms to 200ms, no overshoot.
- Period change: content cross-fades 180ms and numbers re-count.
- Haptics: SegmentTick on segment change, SegmentFrequentTick while scrubbing, Confirm on share.
- Reduced motion (animator scale 0): jump to final states.

## 8. Accessibility

Each chart container has a spoken summary ("Listening time this month: 41 hours, up 12% on October. Busiest day Saturday the 8th."). Decorative glows and grain are hidden from TalkBack. Test at 2.0x font scale: numerals shrink to fit instead of wrapping.

## 9. Data rules (kept from the plan)

- Local day = `(startedAt + tzOffsetMinutes*60000) / 86400000`, so travel never rewrites old days and totals match History.
- Calendar periods Week / Month / Year plus All. Running periods compare against the same elapsed length of the previous one, and say so.
- Counting: a listen counts after 25 seconds, or half of a very short track. Skipped means under 30% played.
- Time and plays can both rank lists, time is the default. Unknown Artist is excluded from artist lists.
- Testing without waiting: injected clock and zone, seeded demo history (1 week / 3 months / 3 years) in Setup.

## 10. Build order (one card per step, shipped and checked before the next)

0. Clean slate (0.15.2): cards deleted, screen is a shell.
1. Period control into the glass header (0.15.3: done, compact bar grows in the glass as the in-page control scrolls away; the title becomes the period name).
2. Hero (0.16.0: built, see docs/STATS_HERO.md).
3. Most played (0.17.0: card built, see docs/STATS_MOST_PLAYED.md), then its full list.
4. Your days (0.18.0: built, see docs/STATS_DAYS.md).
5. Discovery.
6. Your hour (0.19.0: built, ribbon, see docs/STATS_HOUR.md).
7. Showing up.
8. Records.
9. How you listen.
10. Poster picker.
11. Detail screens and doorways wired as each card lands.

## 11. Colour rule (decided)
One accent, Tape Yellow, for emphasis in the data. Semantic colours (green, orange, red) only for status: tracking and permission state, errors. Never in the data itself: listening more or less is not good or bad, so up is yellow and down is quiet grey. Categories (such as apps) use steps of yellow or neutral greys with direct labels. Cover-derived colour only on exported posters.
