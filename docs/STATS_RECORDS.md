# Records card spec (Stats card 8)

**Question:** what are my bests?

Plaques, not tiles: one personal best per row, so each value can be the headline.

- **Four plaques** (a row is hidden when it has nothing to say):
  - Biggest day: the day with the most listening time in the window, value as a duration, its date.
  - Longest streak: the longest run of consecutive listening days in the window (2 or more), as "12 days" with its date range.
  - Most plays in a day.
  - Most replayed: the track you came back to most (needs 2 or more), with its play count.
- **Glyphs** in raised round tiles (a peak, a flame, rising bars, a loop arrow), in line with the listener marks.
- **ALL-TIME BEST tag:** a small yellow tag appears only when nothing in your whole history beats that record. Records are checked against every day you have tracked, not just this window.
- **Sentence above:** "A personal best: your biggest day ever is in this window." when any plaque carries the tag, otherwise "Your biggest day was Sat 8 Nov, with 5h 12m."
- **Motion:** plaques fade up one after another, once per load. Reduced motion jumps to final.
- **Data gate:** hidden until there are 10 plays over 3 days.
- **Pure model:** `Records` (best day, best streak, all-time checks), unit tested. No schema change.
- **Doorway (later):** all records with their dates, and a tap on a record opens that day.
