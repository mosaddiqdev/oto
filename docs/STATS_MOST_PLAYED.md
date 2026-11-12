# Most played card spec (Stats card 3)

**Question:** what did I play most?

## Structure
- Title "Most played" (Bricolage) and one takeaway sentence above the card, computed from real numbers:
  Artists "X was 31% of your listening time." (only when the share is at least 5%), Tracks "X got 3h 20m of your ears.", Albums "X led with 214 plays."
- Card: segmented Artists / Tracks / Albums (40dp, same sliding capsule as the period control), then a small "Ranked by  Time | Plays" toggle right aligned.
- **No. 1 featured:** 88dp cover, "No. 1" label in yellow, name in Bricolage 20sp (2 lines), subtitle, big value and the other metric quiet beside it, a 6dp yellow bar.
- **No. 2 to 5:** rank number, 44dp cover, name, subtitle, value, and a 3dp grey bar under the text. All bars share one scale: the No. 1 value.
- Artists get circular covers, tracks and albums get rounded squares. We have no artist photos, so an artist borrows the cover of their most played track in the window.

## Rules
- Fewer than five rows just shows fewer. Empty tab: "No album information for these listens." (tracks without album text are not in the Albums list).
- Long names ellipsize (2 lines for No. 1, 1 line below). Missing cover shows a quiet tile with the first letter.
- Values use tabular figures. 1,000+ hours show as "1,284h". Plays use thousands separators.
- Accent budget: the No. 1 label and bar only. Rows 2 to 5 are grey.
- Switching tab or metric crossfades the list (180ms) and the card height eases. Bars grow once per load, staggered, clamped to 0..1, snapped when motion is reduced.
- TalkBack reads each row as one item: "Number 2, title, artist. 3h 20m, 41 plays."

## Doorway
"See all" and tapping any row open the full ranked list and the artist / track / album detail screens. Not built yet, so the card has no chevron and no tap target on rows until they exist.
