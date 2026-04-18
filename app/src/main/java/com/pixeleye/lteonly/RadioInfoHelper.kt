package com.pixeleye.lteonly

import android.content.ComponentName
import android.content.Context
import android.content.Intent

object RadioInfoHelper {

    /**
     * Attempts to open the Radio Info menu using direct intents only.
     * Displays a message if the device blocks access or the menu doesn't exist.
     */
    fun openRadioInfo(context: Context) {
        // A consolidated list of known components for testing/radio menus across Android
        val targets = listOf(
            ComponentName("com.android.settings", "com.android.settings.RadioInfo"),
            ComponentName("com.android.settings", "com.android.settings.TestingSettings"),
            ComponentName("com.android.phone", "com.android.phone.settings.RadioInfo"),
            ComponentName("com.android.phone", "com.android.phone.PhoneInterfaceManager"),
            ComponentName("com.sec.android.app.servicemodeapp", "com.sec.android.app.servicemodeapp.ServiceModeApp"), // Samsung
            ComponentName("com.miui.cit", "com.miui.cit.CitActivity"), // Xiaomi
            ComponentName("com.huawei.android.projectmenu", "com.huawei.android.projectmenu.ProjectMenuActivity") // Huawei/Honor
        )

        for (target in targets) {
            try {
                val intent = Intent("android.intent.action.MAIN").apply {
                    component = target
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return // Exit the function immediately upon a successful launch
            } catch (e: Exception) {
                // Catches ActivityNotFoundException or SecurityException
                // Silently ignore and continue to the next component in the list
                continue 
            }
        }

       throw Exception("Radio Info menu not found or access blocked")
    }

    /**
     * Checks if a given intent can be resolved by the system.
     */
    fun canResolveIntent(context: Context, intent: Intent): Boolean {
        return intent.resolveActivity(context.packageManager) != null
    }
}