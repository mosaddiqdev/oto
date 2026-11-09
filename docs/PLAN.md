# Music Stats App: Plan

Status: planning. No code written yet.

## 1. What we're building
An Android app that quietly watches what you play in **any** music app (Spotify, YouTube Music, Poweramp, local players, etc.) and turns it into **stats, history and insights**. Everything stays on your phone.

- Mainly stats. **Local-first**: tracking, stats and history all work with no internet. Personal use.
- Internet is used only for optional enrichment (artist pictures, missing album art, genres). Results are cached on the phone.
- Not tied to any single music service.
- Sharing (image cards) comes later, not in v1.
- Own UI, own tabs, own design. Tempo is a logic reference only.

## 2. Licensing note (important)
The reference repo (avinaxhroy/Tempo) uses AGPLv3 plus custom terms: no commercial use, no monetization, no rebranding. So:
- We do **not** copy its code.
- We write our own implementation from Android's public APIs.
- The thresholds below are just design decisions; we pick our own numbers.

## 3. Tech stack
| Layer | Choice |
| --- | --- |
| Platform | Native Android (the feature needs Android media APIs; no iOS) |
| Language | Kotlin 2.4.x |
| UI | Jetpack Compose, Material 3 (heavily customised theme) |
| Build | AGP 9.3.2+, Gradle 9.5+, JDK 17 |
| Database | Room |
| DI | Manual (`AppContainer`); a framework is overkill here |
| Settings | DataStore |
| Background | Foreground service, WorkManager (watchdog) |
| Navigation | Navigation Compose |
| Images | Coil 3 |
| Charts | Vico, plus custom Compose Canvas for heatmaps |
| Min / compile / target SDK | 26 / 37 / 36 (Compose 1.12 needs compileSdk 37) |
| CI | GitHub Actions builds the APK |

Pin the Compose BOM at project setup time.

## 4. How tracking works
1. A **NotificationListenerService** gives access to active media sessions from any player.
2. We listen to each session's playback state and metadata changes.
3. Play time is accumulated from **position changes**, not a wall clock, so pauses and seeks don't inflate it.
4. One session becomes **one listening event**, saved once.

### Our starting rules (tunable)
- Plays under **25 s** are ignored.
- Under **30%** completion counts as a **skip**; 80% or more is a **full listen**.
- Loops are capped at **3x** track length.
- Same track replayed within **5 min** is flagged as a replay.
- Missing duration falls back to about **3.5 min**.
- Dedupe by session id, plus a short time window.
- **Allowlist of music apps** (default deny). Podcasts, audiobooks, calls and video are filtered out. User can allow or block apps.
- Artist fallback chain: artist, album artist, then "Unknown Artist". Placeholder values are rejected.
- Split multi-artist strings ("feat.", "&", ",") while keeping band names intact.

### Staying alive in the background
- Foreground service (media playback type) plus boot receiver.
- Session saved every ~30 s while playing; recovered after a crash or kill.
- Blocking flush when the service is destroyed.
- WorkManager watchdog (~30 min) that checks the listener is still bound and rebinds if not.
- Onboarding steps for battery optimisation and phone-maker autostart settings.

### Permissions
Notification listener access, foreground service (media playback), post notifications, boot completed, ignore battery optimisations.

## 4b. Artwork and metadata (optional online enrichment)
**Album art, offline first.** The playing app already hands us album art (a bitmap or an image URI) through the media session. We save it locally, so most covers need no internet.

**Artist pictures and genres need a lookup.** The media session never includes them. Tempo fills gaps by trying free sources in a priority order; we do the same, but only with sources that need no login:

| Source | Gives | Needs |
| --- | --- | --- |
| Deezer public API | cover art, artist picture (XL) | nothing |
| iTunes Search API | cover art, artist, genre | nothing; roughly 20 requests/min |
| MusicBrainz + Cover Art Archive | genres, covers, IDs | User-Agent header; 1 request/s |
| Last.fm API (optional) | tags, artist info | free API key |

Skipped in v1: Spotify login (OAuth), audio features (energy, valence, danceability).

**Rules**
- Runs in the background (WorkManager), rate-limited, retried later on failure.
- Fills only gaps: art we already have is never refetched.
- Images cached on disk (Coil); app works fully if a lookup fails.
- Settings toggle: "Fetch artwork online" with a Wi-Fi-only option.
- Privacy: lookups send only artist and track names, never listening history.
- Check each service's terms before any public release. Personal use is fine.

## 5. Data model
- **Track**: id, title, artist string, album, duration, artwork, primary artist id
- **Artist**: id, name, normalised name (unique)
- **Album**: id, title, artist id, artwork
- **TrackArtist**: junction table (role)
- **ListeningEvent**: track id, start time, play duration, completion %, source app, skipped, replay, pause count, seek count, session id, end time, UTC offset at play time (so travel doesn't rewrite past days)
- **AppPreference**: per-app allow/block

Key stats queries: totals, top tracks/artists/albums, hourly, weekday and daily distributions, skip, replay and full-listen counts, most skipped, most replayed.

## 6. Screens
### Bottom tabs (4)
1. **Now**
   - Current track, source app, live progress.
   - Today at a glance: plays, listening time, top artist so far.
   - Empty state when nothing is playing, plus tracking-health indicator.
2. **Stats** (the hero tab)
   - Range chips: Today, Week, Month, Year, All.
   - Headline numbers: time listened, plays, unique tracks, artists.
   - Top tracks, artists, albums.
   - Listening heatmap (hour x weekday) and daily trend.
   - Skip rate, replay rate, completion rate.
   - Streaks and records.
3. **History**
   - Chronological timeline grouped by day.
   - Search, filter by app and date range, skip and full-listen markers.
4. **Library**
   - Songs, Artists, Albums, ranked by plays or time.
   - Search and sort.

### Other screens
- **Onboarding**: welcome, how it works and privacy, grant notification access, battery and background setup.
- **Song details**, **Artist details**, **Album details**: play counts, time, first and last played, trend, history.
- **Settings** (gear icon): tracked apps, tracking rules (minimum play, skip threshold), background protection helper, backup and export, theme.

### Later (not v1)
Shareable image cards, home screen widgets, Last.fm or other imports, artwork and metadata enrichment.

## 7. Build order
Each step ends with a working APK from GitHub Actions.

0. **Project skeleton** and the GitHub Actions workflow. DONE.
1. **Tracking engine and database**, with a bare debug screen to verify events. BUILT, needs testing on a real phone.
   - Pure Kotlin `PlaybackTracker` with unit tests (run in CI), Room schema, `NotificationListenerService`, debug screen.
1b. **Background hardening.** BUILT, needs testing on a real phone.
   - Open listens saved every ~30 s and resumed or recovered after a process kill; boot/update receiver; 30-minute watchdog that rebinds the listener; battery-restriction card.
   - Not done on purpose: a foreground-service notification. The listener is system-bound; add one only if phone testing shows kills.
2. **Onboarding and permissions.** Includes the "Allow restricted settings" step for sideloaded APKs on Android 13+.
3. **Design system**: colors, type, components.
4. **Now** tab.
5. **History** tab.
6. **Stats** tab.
7. **Library** and detail screens.
8. **Settings**, tracked apps, background protection, backup and export.
9. **Polish**, release signing, performance pass.

## 8. Decisions made
- Focus: stats, local-first (works offline; optional online enrichment for artist pictures), personal use.
- Sharing: later.
- Tabs: Now, Stats, History, Library (proposed by Claude).
- Theme: charcoal black, monochrome, one accent color, semantic colors only where needed.

## 9. Open items
- App name and palette (see design notes).
- Final decision on Android target SDK at setup.
