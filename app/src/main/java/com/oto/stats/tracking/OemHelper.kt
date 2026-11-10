package com.oto.stats.tracking

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

enum class Oem(val label: String) {
    XIAOMI("Xiaomi / Redmi / POCO"),
    OPPO("Oppo / Realme / OnePlus"),
    VIVO("Vivo / iQOO"),
    SAMSUNG("Samsung"),
    HUAWEI("Huawei / Honor"),
    OTHER("Your phone"),
}

/** A button that tries to open a phone-maker settings page. Several candidates because they differ by version. */
class OemShortcut(val label: String, val intents: List<Intent>)

class OemGuide(val oem: Oem, val steps: List<String>, val shortcuts: List<OemShortcut>)

/**
 * Many phone makers add their own battery manager on top of Android. It can stop a
 * background app even after "unrestricted battery" is allowed, and swiping the app
 * from Recents may force-stop it. These steps are the known fixes.
 */
object OemHelper {

    fun detect(): Oem {
        val id = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        return when {
            listOf("xiaomi", "redmi", "poco").any { it in id } -> Oem.XIAOMI
            listOf("oppo", "realme", "oneplus").any { it in id } -> Oem.OPPO
            listOf("vivo", "iqoo").any { it in id } -> Oem.VIVO
            "samsung" in id -> Oem.SAMSUNG
            listOf("huawei", "honor").any { it in id } -> Oem.HUAWEI
            else -> Oem.OTHER
        }
    }

    fun guide(context: Context, oem: Oem = detect()): OemGuide = when (oem) {
        Oem.XIAOMI -> OemGuide(
            oem,
            listOf(
                "Autostart: turn ON for Oto.",
                "Battery saver: Settings, Apps, Manage apps, Oto, Battery saver, then choose No restrictions.",
                "Lock in Recents: open Recents, press and hold the Oto card (or drag it down) and tap the lock. Without this, swiping Oto away stops tracking.",
            ),
            listOf(
                OemShortcut(
                    "Open Autostart",
                    listOf(
                        component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                        component("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity"),
                    ),
                ),
                OemShortcut(
                    "Open Battery saver",
                    listOf(
                        component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                            .putExtra("package_name", context.packageName)
                            .putExtra("package_label", "Oto"),
                    ),
                ),
            ),
        )
        Oem.OPPO -> OemGuide(
            oem,
            listOf(
                "Settings, Apps, App management, Oto, Battery usage: allow background activity.",
                "Turn ON Auto-launch (or Allow auto-start) for Oto if you see it.",
                "Lock in Recents: open Recents, long-press the Oto card and tap Lock.",
            ),
            listOf(
                OemShortcut(
                    "Open Auto-launch",
                    listOf(
                        component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                        component("com.oplus.battery", "com.oplus.startupapp.view.StartupAppListActivity"),
                        component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                        component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                    ),
                ),
            ),
        )
        Oem.VIVO -> OemGuide(
            oem,
            listOf(
                "Settings, Battery, Background power consumption, Oto: allow high background power use.",
                "Autostart: turn ON for Oto.",
                "Lock in Recents: pull the Oto card down until the lock shows.",
            ),
            listOf(
                OemShortcut(
                    "Open Autostart",
                    listOf(
                        component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                        component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                    ),
                ),
            ),
        )
        Oem.SAMSUNG -> OemGuide(
            oem,
            listOf(
                "Settings, Battery, Background usage limits, Never auto sleeping apps: add Oto.",
                "Make sure Oto is not listed under Sleeping apps or Deep sleeping apps.",
                "Lock in Recents: tap the Oto icon in Recents and choose Keep open.",
            ),
            emptyList(),
        )
        Oem.HUAWEI -> OemGuide(
            oem,
            listOf(
                "Settings, Battery, App launch, Oto: turn off Manage automatically, then allow Auto-launch, Secondary launch and Run in background.",
                "Lock in Recents: pull the Oto card down until the lock shows.",
            ),
            listOf(
                OemShortcut(
                    "Open App launch",
                    listOf(
                        component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                        component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                    ),
                ),
            ),
        )
        Oem.OTHER -> OemGuide(
            oem,
            listOf(
                "Settings, Apps, Oto, Battery: choose Unrestricted.",
                "If your phone has a battery or app-launch manager, allow Oto to run in the background.",
                "Lock the app in Recents if your phone offers it.",
            ),
            emptyList(),
        )
    }

    /** Tries each candidate in turn. Returns false if none opened. */
    fun launch(context: Context, shortcut: OemShortcut): Boolean =
        shortcut.intents.any { intent ->
            try {
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                false
            }
        }

    private fun component(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls))
}
