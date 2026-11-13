# Discovery card spec (Stats card 5)

**Question:** did I find new music, or loop old favourites?

- **Sentence above:** "18 of 41 tracks were new to you." / "Nothing new this time. You stayed with familiar favourites." / "Every track was new to you."
- **Headline:** the count of new tracks as a lit numeral with "of 41 tracks", and new artists on the right.
- **One bar:** the new share lit yellow, the familiar rest quiet, with "New" and "Familiar - N" beneath. It fills once.
- **Best finds:** the top three new tracks of the period, by time listened, with covers and plays. These are real discoveries, not a count.
- **Footer:** "You also replayed songs 12 times, coming back the same day." only when there were replays (same song twice in one day).
- **Meaning of new:** a track whose very first play ever fell inside the window. Not shown for All time or at the very start of tracking, where "new" would be meaningless.
- **Data:** `newTracksTop` and `newArtistCount` queries (new artists exclude Unknown Artist), no schema change.
- **Doorway (later):** the full list of new tracks and artists.
