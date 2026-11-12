# Hero card spec (Stats card 2)

**Question:** how much did I listen, and is that more than usual?

## Look
Dark card (#151515), 28dp radius, rim of light on the top edge (white 14% fading to 4%), fine grain. Yellow is light, not paint: the lead numeral is a vertical gradient (pale warm top, Tape Yellow, deeper gold) with a soft yellow glow, and a warm radial glow sits behind it. The glow warms up with the count-up, so the card seems to switch on. Minutes are white, units grey. No cover art, no flat yellow field.

## Numeral rules (works from 0 minutes to 10,000+ hours)
| Range | Shown | Lead size |
|---|---|---|
| under 1 hour | minutes and "m" | lead may take 55% of the inner width |
| 1 to 999 hours | hours, "h", minutes, "m" | lead takes at most 50% of the width |
| 1,000 hours and up | hours with thousands separators and "h", no minutes | lead takes at most 70% |

- Size = width available x share / (0.53em x characters), commas count half, clamped to 48..132sp. Fixed from the FINAL value, so the numeral never resizes during the count-up.
- Minutes numeral 40% of lead, units 20% (20..30sp). Font scale is capped at 1.1 inside the card.
- The same function formats comparison values (`formatDuration`): 1,284h, never 1284h 30m.

## Under the numerals
- **Comparison** when a previous window with listening exists: two bars on one scale. Yellow is this period with its percent change, grey is the comparison window (same elapsed days when the period is still running, and it says "Same days of October").
- **Facts** otherwise (All time, first period): plays, tracks, average per day (or per active day).
- **Sentence:** difference against the comparison, then the own-history rank ("Your 2nd best start to a month of 14"), falling back to a human-scale equivalent (albums, days, years of nonstop music).

## Motion and access
Card enters once. Count-up 1200ms, comparison bars fill after it, reduced motion snaps to final. TalkBack reads "Listening time, November: 41 hours 12 minutes. ..." as one element.

## Doorway
Opens the period archive (every week/month/year total, compare any two). Not built yet, so the card is not tappable and shows no chevron until it is.
