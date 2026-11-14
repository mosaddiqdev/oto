# Records card spec (Stats card 8), v3 "Your peaks, one card"

v1 (tiles with icons) read as a generic achievements list. v2 (tabs, one record at a time) hid two of three records behind taps. v3 shows every record at once in one card, no tabs, and puts them in your own history.

**Question:** what are my bests, and how big are they really?

- **Hero:** the biggest day as the lit giant numeral ("5h 12m"), with its date and a yellow ALL-TIME BEST tag when true.
- **One skyline, three marks.** Every day of the window is a column (peak-preserving buckets when more than 100). Grey columns are your days. The record day is lit yellow. The streak days are tinted yellow and a **bracket** under the baseline spans them. The most-plays day wears a **ring** above its column. No text on the chart except the dashed all-time line.
- **Two supporting stats** side by side under the chart, each keyed by its own mark (bracket = Streak, ring = Most plays): value, dates, and either "All-time best" (yellow) or "Longest ever 20" / "Best ever 91" (tertiary).
- **Honest context:** if the biggest day is not your all-time best, a dashed line with "All-time best 9h 05m" shows where it sits; the scale grows to include it.
- A missing record (no streak yet) simply drops its block; the card never shows tabs.
- **Motion:** columns rise left to right, then the ring fades in, then the bracket draws; ease-out, no overshoot; reduced motion jumps to the end.
- **Data gate:** hidden until 10 plays over 3 days.
- **Pure models:** `Records`, `Skyline` (tested). **Doorway (later):** all records with dates; tapping the lit day opens it.
