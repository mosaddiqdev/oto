package com.oto.stats.tracking

/** Which apps we listen to. Default-deny: unknown apps are ignored unless the user opts in. */
object MusicApps {

    val known: Map<String, String> = mapOf(
        "com.spotify.music" to "Spotify",
        "com.spotify.lite" to "Spotify Lite",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "com.apple.android.music" to "Apple Music",
        "com.amazon.mp3" to "Amazon Music",
        "deezer.android.app" to "Deezer",
        "com.aspiro.tidal" to "Tidal",
        "com.soundcloud.android" to "SoundCloud",
        "com.jio.media.jiobeats" to "JioSaavn",
        "com.gaana" to "Gaana",
        "com.bsbportal.music" to "Wynk Music",
        "com.hungama.myplay.activity" to "Hungama",
        "com.maxmpz.audioplayer" to "Poweramp",
        "com.sec.android.app.music" to "Samsung Music",
        "com.miui.player" to "Mi Music",
        "com.heytap.music" to "HeyTap Music",
        "com.android.music" to "Music",
        "in.krosbits.musicolet" to "Musicolet",
        "code.name.monkey.retromusic" to "Retro Music",
        "com.pandora.android" to "Pandora",
        "com.audiomack" to "Audiomack",
        "com.anghami" to "Anghami",
        "com.netease.cloudmusic" to "NetEase Cloud Music",
        "ru.yandex.music" to "Yandex Music",
    )

    /** Never tracked, even with "track all apps": messaging, browsers and video apps. */
    val neverTrack: Set<String> = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram",
        "org.thoughtcrime.securesms",
        "com.facebook.orca",
        "com.facebook.katana",
        "com.instagram.android",
        "com.snapchat.android",
        "com.discord",
        "com.viber.voip",
        "com.google.android.apps.messaging",
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser",
        "com.google.android.youtube",
        "com.netflix.mediaclient",
        "in.startv.hotstar",
        "com.amazon.avod.thirdpartyclient",
    )

    fun labelFor(packageName: String): String =
        known[packageName] ?: packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
