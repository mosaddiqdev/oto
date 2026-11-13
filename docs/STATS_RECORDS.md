# Records card spec (Stats card 8), v2 "Your peaks"

v1 (tiles with icons) was dropped: it read like a generic achievements list. A record only means something next to everything else you did, so the card shows the record IN your own history.

**Question:** what are my bests, and how big are they really?

- **One record at a time** through the same sliding switch as Most played: Biggest day, Streak, Most plays (a tab is hidden when it has nothing to say).
- **The numeral is the graphic**, lit like the hero: "5h 12m", "12 days in a row", "61 plays in a day". Under it the date (or date range for a streak).
- **The skyline:** every day of the window as a column (several days per column when there are more than 100, always keeping the peak so a record is never averaged away). Quiet grey columns, with the record day, or every day of the streak, lit yellow. No icons.
- **Honest context:** when the record is not your all-time best, a dashed line shows where your best sits and names it ("All-time best 9h 05m"); the scale grows to include it, so you can see how far above it is. For streaks it says "Your longest ever is 20 days". When it IS your all-time best, a small yellow ALL-TIME BEST tag replaces the line.
- **All time** spans everything since tracking began.
- **Sentence above:** "A personal best: your biggest day ever is in this window." or "Your biggest day was Sat 8 Nov, with 5h 12m."
- **Motion:** columns rise left to right with a tiny stagger, replayed when you change record, ease-out, no overshoot. Reduced motion jumps to final.
- **Data gate:** hidden until there are 10 plays over 3 days.
- **Pure models:** `Records` and `Skyline` (peak-preserving downsampling), unit tested.
- **Doorway (later):** all records with dates, tapping the lit day opens that day.
