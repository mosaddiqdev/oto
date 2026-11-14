# Doorways and detail pages: plan

Status: plan. Nothing here is built yet. Builds on docs/STATS.md section 3 ("every card is a doorway").

## 1. What Tempo does (inspiration only, no code copied)

- Every artist, album and track has its own full-screen route with a floating back button and action buttons.
- Each page has a large hero (cover or photo), a "masthead" row of big stats, then a long scroll of sections. Artist: fan standing, listening journey, top songs, top albums. Album: tracklist with per-track plays. Song: temporal rhythm chart, "acoustic DNA" meters, listener affinity, a listening "odyssey", playback fidelity telemetry, a streaming-links hub.
- Management actions live on the pages: rename, merge, split, change cover, delete (about a quarter of the code).
- The accent colour comes from the cover art on each page.

What works: the hero plus masthead is a strong first screen, and an album page where the tracklist IS the content is the right idea.
What does not: the song page is nearly 3,000 lines and 10+ sections, so it is a dashboard, not a page. Telemetry and "acoustic DNA" answer questions nobody asked. Per-page colour changes break the product's identity. Admin actions compete with the content.

## 2. Our rules

1. **One skeleton for all three pages.** Learn it once: top bar, hero, three facts, one chart, the children list, a few quiet facts. Never more than four sections.
2. **Local first.** Every page is complete from your own listening data alone. Online metadata (photo, bio, tracklist, discography) only adds to it, never gates it.
3. **The numeral is the hero** (lit, like Stats), the image supports it. Charcoal and one yellow accent on every page; no per-page colour.
4. **Every name is a doorway.** An artist name on a track page opens the artist, an album opens the album, a ranked row opens its page. A press state (0.97 scale) and a quiet chevron tell you which things open.
5. **Window carries in.** Tapping from "This month" opens the page on This month, with a two-way switch (This period / All time) at the top of the page. Arriving from anywhere else opens on All time.
6. **Nothing to manage on the page.** Rename, merge, change cover and delete live behind one overflow menu and come last.

## 3. The skeleton

| Block | Artist | Album | Track |
|---|---|---|---|
| Top bar | back, window switch, overflow | same | same |
| Hero | photo fading into charcoal, name, lit numeral (time), "412 plays" | cover, title, artist (tappable), lit numeral | cover, title, artist and album (tappable), lit numeral (plays) |
| Facts row (3) | first heard, last heard, days played | tracks heard / tracks, first heard, last heard | first heard, skip rate, completion |
| Chart | your history with them: skyline by week or month, first and peak lit | heard-through bar per track | your history with it: skyline since first heard, replays marked |
| Children | Top tracks (ranked rows), Albums (covers) | The tracklist: rows with a fill bar by plays; unplayed tracks dim | Listens: the last ten sessions (date, time, app, completion) |
| Quiet facts | hour you play them most, "your Nth play" milestones | same idea | longest streak playing it, favourite hour |

Online enrichment slots in without changing the layout: artist bio under the hero (three lines, expand), "You've heard 11 of 24 songs" on the artist (coverage), unplayed tracks appear in the album tracklist, and a real cover and photo replace the generated ones.

## 4. Navigation

- A small in-app back stack in the shell (no new dependency): routes are Artist(id), Album(id), Track(id), plus later PeriodArchive and the full Most played list. Saved with `rememberSaveable`.
- Transition: slide in from the right with a short fade, 300 ms, ease-out. The bottom bar slides away on detail pages. Back gesture and the back button pop the stack (predictive back via `BackHandler`).
- Rows in Most played, Discovery and the Hero's comparison become doorways first; the rest follow.

## 5. Build order

1. Navigation stack, press states and the Track page (smallest, fully local).
2. Artist page.
3. Album page.
4. Wire every list row and name to its page.
5. Metadata layer: cache tables with a Room migration, MusicBrainz and Wikipedia, Deezer and iTunes images, stricter artist-photo matching.
6. Period archive and the full Most played list.
7. Poster picker.

## 6. Data needed (all local)

Per-entity queries over `listening_events`: totals in a window or all time, first and last heard, days played, weekly or monthly buckets, hour histogram, skip rate and completion, recent sessions, child lists (tracks of an artist, albums of an artist, tracks of an album). Most can reuse the existing top-rows queries filtered by an id.
