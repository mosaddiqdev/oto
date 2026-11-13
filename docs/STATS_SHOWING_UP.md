# Showing up card spec (Stats card 7)

**Question:** how consistent am I?

Not a grid of identical squares. Each day is a bead, and days in a row are strung together, so a streak is a visible chain.

- **Sentence above:** "You listened on 24 of 30 days. Longest run: 12 days." (All time says "in the last 12 months".)
- **Headline:** the count of days with listening as a big lit numeral ("24 of 30"), same voice as the hero. Right side: the live streak (yellow, only when the period includes today and the streak is 2 or more days) otherwise the longest run in the window.
- **Week and month:** a calendar of large beads (30dp) with the day number inside, weekday initials on top (locale first day of week). Listening days are yellow, brighter the more you listened (four levels by quartile within the range); no-listening days are just a grey number; future days are dimmed. Runs of 2 or more days sit inside a soft capsule, so a streak reads as one chain. A run never wraps onto the next row.
- **Year and All:** twelve strings of small beads, one row per month, 31 columns. All time shows the last 12 months.
- **Unknown is not zero:** days before tracking began and days in the future are drawn differently from a real day with no listening, and are not counted in "of N".
- **Today** has a thin ring. **Tap or drag** to read a day on one fixed line under the chart ("Sat 8 Nov, 5h 12m, 61 plays"), with a light tick. The card height never changes when you read a day.
- **Motion:** beads fade and grow in a diagonal sweep, then the capsules appear, once per load. Reduced motion jumps to final.
- **Data gate:** hidden until there are 10 plays over 3 days.
- **Pure model:** `ShowingUp` (levels, elapsed, active, longest run), unit tested.
- **Doorway (later):** year view and streak history.
