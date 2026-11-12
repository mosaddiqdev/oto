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

## Structure (v2, 0.16.1)
1. **Eyebrow row:** "LISTENING TIME . NOVEMBER" left, **delta pill** right (yellow tint when up, quiet grey when down, since listening less is not an error).
2. **Numerals** (rules above).
3. **Comparison track:** ONE track. Yellow fill is this period, a white tick marks where the comparison window ended up on the same scale (8% headroom). Fill past the tick means ahead. Legend below: yellow square with value and period name, white tick with value and comparison name.
4. **Facts** instead of the track when there is nothing to compare against (All time, first period): plays, tracks, per day.
5. **Story line:** one line that adds meaning, never repeats the card: own-history rank, else the plain difference, else a human-scale equivalent.

## Height changes
The card sizes itself to its content (animateContentSize, 260ms decelerate, applied before clip and padding), so switching between comparison and facts, or between a one-line and two-line story, eases instead of jumping, and the cards below glide with it.

## Motion and access
Card enters once. Count-up 1200ms, comparison bars fill after it, reduced motion snaps to final. TalkBack reads "Listening time, November: 41 hours 12 minutes. ..." as one element.

## Doorway
Opens the period archive (every week/month/year total, compare any two). Not built yet, so the card is not tappable and shows no chevron until it is.
