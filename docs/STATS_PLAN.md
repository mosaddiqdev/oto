# Stats tab: structure and architecture plan

Status: plan only. No visual design yet. Nothing here is built.

## 1. What the tab is for

Now answers "what is playing and how is today going". **Stats answers "what is my listening like over a stretch of time, and how does it compare to before".** It is the reflective tab: you open it to look back, not to act. History is the raw log; Library is the catalogue; Stats is the *summary and the story*.

Rule that keeps the tab honest: every number on it is derived from `listening_events`, the range is always visible, and the same range drives every section.

## 2. What users expect (research)

Sources: stats.fm, Last.fm, ListenBrainz, Spotify Wrapped alternatives, and UX guidance on time-range charts.

Table stakes (every serious stats app has these):
- Top artists, tracks, albums for a chosen time window.
- Total listening time and play count.
- Selectable windows (about 4 weeks / 6 months / lifetime on stats.fm; day to all-time on Last.fm).
- Activity over time (daily or weekly chart) and time-of-day patterns.

What users ask for and apps often lack:
- **Compare ranges** (this month vs last month; "recently vs ever"). stats.fm has open feature requests for it.
- **Real play counts, not opaque rankings.** Users distrust rankings with no numbers behind them.
- **Clarity on why two apps disagree.** Almost always a different window or data source, so the window must be named.
- **Complete history caveat.** "All time" only covers what was tracked; users resent being misled. Oto should say "since <first tracked date>".
- Shareable visuals (deferred, later phase).

UX pitfalls to design around (time-range UX guidance):
- Separate rolling windows ("Last 30 days") from calendar periods ("October"). Name them precisely.
- Show the resolved start and end dates next to the data.
- Never compare a partial period with a complete one without saying so (compare equal elapsed time).
- Label averages with their denominator (per calendar day vs per active day).
- Time zones: totals must match what History shows for the same day.
- Sparse data: one play must not produce a "trend".
- Distinguish a real zero from no data.
- A slow query must never overwrite a newer selection.
- Future periods are not selectable.

## 3. Product decisions

1. **Calendar periods plus All time, with Prev/Next navigation.** Chips: Week, Month, Year, All. Week/Month/Year are calendar periods you can step back through (Oct, Sep, ...). All has no stepping. Rolling windows are not offered in v1; simpler to explain and compare. (Custom range: later.)
2. **Default: Month, current.** Enough data to be interesting after a few days, and "this month so far" is how people think.
3. **Comparison is built in, not a separate mode.** Headline numbers show a delta against the previous equivalent period. If the current period is incomplete, compare against the same elapsed length of the previous one and say so.
4. **Ranking metric: time listened by default, with a plays toggle.** Time and plays disagree for long tracks; letting the user flip is the honest answer. The metric is shared across all top lists.
5. **Top lists show a short list (5) with "See all"**, which opens a full ranked list. Tapping an item goes to its detail screen (built later; row is inert until then).
6. **No genres in v1.** We have no genre source offline. Revisit only with optional enrichment.
7. **Albums come from the track's album text.** There is no album table; group by normalised (album, primary artist). Tracks without an album are excluded from the albums list, not shown as "Unknown".

## 4. Content, top to bottom

1. **Period bar**: chips (Week/Month/Year/All), the resolved date range text, prev/next arrows (next disabled at the current period).
2. **Headline**: listening time (hero), plays, unique tracks, unique artists. Each with a delta vs the previous comparable period, hidden when the previous period has no data.
3. **Activity**: bars across the period. Week = 7 days, Month = days, Year = 12 months, All = months or years chosen by span. Shows an average labelled with its denominator.
4. **Top artists** (5, See all).
5. **Top tracks** (5, See all).
6. **Top albums** (5, See all).
7. **When you listen**: hour-of-day distribution and weekday distribution, plus a peak line ("Most active: Friday evenings"). A full hour x weekday heatmap is a Year/All-only extra.
8. **Habits**: completion rate (full listens), skip rate, replay count, most skipped and most replayed track. Streak (consecutive days with listening) and longest day / longest streak records.
9. **By app**: share of time per source app (Spotify, YouTube Music, ...). Useful because Oto tracks every app.

Order is by value-per-scroll: headline and top lists come before pattern analysis.

Empty states:
- No events at all: explains that stats appear after the first counted listen, and links to tracking status.
- Period has no events: "No listening in <period>", prev arrow still works.
- Very little data (fewer than about 3 days or 10 plays): show headline and lists, hide the distributions and habits that would be misleading, with a one-line "more after a few days of listening".

## 5. Data and queries

Source: `listening_events` joined to `tracks`, `track_artists`, `artists`. All range queries take `[startMs, endMs)` in UTC epoch.

**Local day rule.** Every event stores `tzOffsetMinutes` at play time, so the local day of an event is `(startedAt + tzOffsetMinutes*60000) / 86400000`. All grouping uses that, never the device's current offset, so travel does not rewrite old days and totals match History. The existing `dailyTotals` uses a single offset and will be replaced by this rule.

**Period boundaries** are computed in Kotlin (a pure `StatsPeriod` type using `java.time` with the device zone) and passed to SQL as epoch ms. Boundaries are the user's current zone; grouping uses the per-event offset. The small mismatch around a zone change is accepted and documented.

Queries (all `Flow`, all parameterised by range):
- `headline(start, end)`: plays, sum playedMs, distinct tracks, distinct artists, full-listen count, skip count.
- `activityBuckets(start, end, bucket)`: playedMs and plays per local day (month/week) or per local month (year/all).
- `topTracks(start, end, orderBy, limit)`: group by trackId.
- `topArtists(start, end, orderBy, limit)`: via `track_artists`. A multi-artist track credits each artist; play time is credited in full to each (stated in the plan, matches what other apps do).
- `topAlbums(start, end, orderBy, limit)`: group by (album, first artist), album not null.
- `hourDistribution` / `weekdayDistribution`: local hour and weekday from the per-event offset.
- `appShare(start, end)`: group by sourcePackage.
- `habits(start, end)`: skip, replay (events with the same track and a prior event in the same session window, definition to be pinned in tests), most skipped and most replayed.
- `activeDays(start, end)`: distinct local days, feeds streaks computed in Kotlin.
- `firstEventAt()`: for the All label and the earliest navigable period.

Indexes: add `listening_events(startedAt)` and `listening_events(trackId)` if missing. This is a schema change, so it needs migration 1 to 2 and a committed schema, per the existing guard.

Counting rule: only completed-or-saved events count (as they are today). The currently open session is included, as it is already saved by checkpoint, so Stats and Now agree.

## 6. Architecture

```
StatsScreen (Compose, stateless)
   ^ StatsUiState (immutable)
StatsViewModel
   - selection: MutableStateFlow<Selection(range kind, offset, metric)>
   - uiState = selection.flatMapLatest { sel -> repository.statsFor(sel.period, sel.metric) }
                         .map(::toUiState)   // includes previous-period compare
   ^ repository
StatsRepository (in ListeningRepository or a sibling)
   - combines the DAO flows for one period into a StatsSnapshot
   ^
ListeningDao (range queries above)
```

Decisions:
- **Unidirectional.** The screen only renders `StatsUiState` and sends events (select range, step, toggle metric).
- **`flatMapLatest`** gives the "stale result never overwrites a newer selection" guarantee for free.
- **Pure logic is separated and unit tested:** `StatsPeriod` (boundaries, labels, previous equivalent period, elapsed-length compare), streak calculation, delta formatting, peak-time phrasing, the "enough data" gate.
- **Room is the live source.** Flows re-emit when new listens are saved, so Stats updates while you listen. Heavy sections are combined with `debounce` (~300 ms) to avoid recomputing on every checkpoint save.
- **Sections load independently** where it helps: headline and top lists first; distributions and habits can arrive a moment later without blocking.
- **ViewModel dependency:** the app uses manual DI (AppContainer), so a small `viewModel` factory is added; adds `lifecycle-viewmodel-compose` as a dependency. State survives rotation and tab switches (selection kept in the ViewModel, scoped to the activity).
- **Time zone and clock are injected** (`Clock`, `ZoneId`) so tests are deterministic.
- Scroll header and overscan behaviour reuse the existing `ScreenColumn` shell contract.

## 7. Testing

- `StatsPeriod`: week start (locale first day of week), month lengths, year, DST days (23/25 hours), previous equivalent and elapsed-length comparison, no future navigation.
- Local-day grouping with mixed `tzOffsetMinutes`, including an event near midnight and a travel case.
- DAO queries against an in-memory Room database (instrumented-free where possible via Robolectric is not available, so keep SQL simple and test the pure parts; add a small androidTest later if needed).
- Streak and records calculation.
- "Not enough data" gate and delta hiding.
- Counting rule: skipped and short plays handled identically to History.

## 8. Edge cases

- Same song under two spellings or "Unknown Artist": handled by the existing identity key; Unknown Artist is excluded from the Artists list.
- Track-all-apps mode counts short Reels/podcasts: By-app section makes this visible, and a later setting can exclude apps.
- Events spanning midnight: attributed to the day they started.
- Very large history: top lists use `LIMIT`; "See all" paginates.
- A period at the very start of tracking: delta hidden because the previous period is incomplete.

## 9. Out of scope for v1

Genres, mood analysis, friends, challenges and gamification (Tempo has these; we are deliberately not copying them), custom date ranges, share cards, year-in-review story. All can sit on this architecture later.

## 10. Build order

1. Pure logic with tests: `StatsPeriod`, streaks, deltas, data gate.
2. Schema: indexes, migration 1 to 2, committed schema.
3. DAO queries and repository snapshot.
4. ViewModel and state.
5. Screen skeleton with real data and all empty states (basic layout only).
6. Visual design pass (separate step, needs your approval of this structure first).
7. Detail screens for "See all" lists, then wire taps.
