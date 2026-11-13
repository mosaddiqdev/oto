# How you listen card spec (Stats card 9)

**Question:** who am I as a listener?

- **The label with a face:** a 64dp tile with a small line-weight mark per label (crescent for Night Owl, sun for Early Bird, an orbit for Loyalist, a loop arrow for On Repeat, a compass star for Explorer, skip arrows for Picky, an even bar level for Steady Listener, rippling rings for Just Getting Started), then the name in Bricolage and the rule behind it ("41% of your listening happens after midnight"). The rule is always shown: no label without its evidence.
- **Four spectrums**, each a position between two ends with the real number named in the middle:
  - Daytime to After dark: share of listening in the evening and at night.
  - Finisher to Skipper: share of listens that were skipped (under 30% played).
  - Familiar to Explorer: share of tracks new to you in this period.
  - Wide taste to Loyal: share of time that went to your top artist.
  A tick marks the middle; the yellow segment runs from the middle to your dot, so you see which way you lean.
- Spectrums that need patterns (the first) are hidden until there is enough data (10 plays over 3 days). Explorer and Loyal appear only when they are meaningful.
- **Motion:** each dot glides from the middle to its place, one after another, ease-out, no overshoot, once per load. Reduced motion jumps to final.
- **TalkBack:** one summary with the label, the rule and the four positions.
- No predictions, no "age", nothing that cannot be traced to a number.

## Evidence lines name names (0.21.1)
Where we know who or what, the line says it instead of a generic phrase: Loyalist "62% of your listening time went to NF.", On Repeat "...most of all "Mansion".", Picky "...most often "Intro".", Explorer "...: 18 new tracks.", Steady Listener "No habit dominates. Your top artist, NF, took 20% of your time." Falls back to the generic wording only when the name is not available.
