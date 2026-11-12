# Your days card spec (Stats card 4)

**Question:** how did my listening spread over the period?

- Takeaway sentence above the card: "Your busiest day was Sat 8, with 5h 12m." / "October was your biggest month, with 41h." / "Just one day of listening so far."
- Readout row inside the card: big value and a quiet line (date, plays) for the selected bar, and an "avg" legend with the dashed line sample.
- Chart: one bar per day (week, month), per month (year, all up to 36 months) or per year. No axis, no gridlines. The busiest bar is lit with the yellow gradient, every other bar is dark grey. Touch or drag to read any bar; it turns yellow, the others stay grey. Selection persists until the period changes.
- Zero days: a 3dp stub. Before tracking began or in the future: a small dot, never a bar, so a real zero is never confused with no data.
- Average: dashed line across the chart, mean of tracked bars (needs 3 or more), named in the legend ("avg 1h 22m").
- Axis labels: week, weekday initials; month, day numbers every 7th; year, month initials; all, year marks. Selected label turns white.
- Motion: bars rise from the baseline, each on its own clock 30ms after the last, ease-out, no overshoot, once per load. Reduced motion jumps to final.
- Haptic: SegmentFrequentTick only when the selected bar changes.
- TalkBack: one summary (unit, highest, average).
- Doorway (later): day-by-day list, and a tap on a bar opens what played that day.
