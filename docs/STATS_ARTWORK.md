# Artwork and enrichment

What Oto knows without the network: the track cover from the notification (saved by `ArtStore`, 300px).
Everything else is optional enrichment, off with one switch in Setup ("Fetch artist photos"). With it off Oto never touches the network.

## 0.17.1: artist photos
- Source: Deezer public search, no key, no login: `api.deezer.com/search/artist?q=<name>`. Only the artist name is sent.
- Match: exact normalised name (case, accents, punctuation ignored); several matches pick the one with the most fans; Deezer's "no photo" placeholder is rejected.
- Save: `picture_xl` (1000px) shrunk to 300px JPEG in `ArtStore` under `artist:<id>`.
- On demand: only for artists a screen is showing (Most played, Artists tab). Until the photo arrives the circle shows the cover of the artist's top track, then the photo fades in.
- Polite: one request at a time, 250ms gap, only on an unmetered network, failures remembered for 14 days (`.none` marker), network errors are not remembered.
- Needs the INTERNET permission, which Oto did not have before. It is used by nothing else.

## Later
- Better track and album covers when the notification cover is small or missing: iTunes Search (rewrite artwork URL to 1200px), Cover Art Archive as backup.
- Genres: MusicBrainz tags, much later.
- Onboarding asks for the choice explicitly (default on now while it is a personal app).
- Not verified live from the build sandbox (it blocks outside sites); first real check is on the phone.
