package com.onebitmonochrome.blacksbbox.view.x5

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import top.niunaijun.blackbox.BlackBoxCore

class X5RedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri: Uri = intent?.data ?: run {
            finish()
            return
        }

        var message = "X5 relay: обработчик не найден"
        try {
            val probe = Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addCategory(Intent.CATEGORY_BROWSABLE)

            for (user in BlackBoxCore.get().users) {
                val candidates = BlackBoxCore.getBPackageManager().queryIntentActivities(
                    probe,
                    PackageManager.MATCH_DEFAULT_ONLY,
                    null,
                    user.id
                )

                val chosen = candidates.firstOrNull {
                    it.activityInfo?.packageName == "ru.pyaterochka.app.browser"
                } ?: candidates.firstOrNull()

                val info = chosen?.activityInfo ?: continue
                message = "X5 relay: user=" + user.id + " -> " + info.packageName + "/" + info.name

                val forward = Intent(probe)
                    .setComponent(ComponentName(info.packageName, info.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

                BlackBoxCore.getBActivityManager().startActivity(forward, user.id)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                finish()
                return
            }
        } catch (t: Throwable) {
            message = "X5 relay: " + t.javaClass.simpleName
            Log.w("X5Redirect", "relay failed", t)
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
