package com.onebitmonochrome.blacksbbox.view.x5

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import top.niunaijun.blackbox.BlackBoxCore

class X5RedirectActivity : Activity() {
    private var callbackUri: Uri? = null
    private var targetUserId: Int? = null
    private var targetComponent: ComponentName? = null
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        callbackUri = intent?.data

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 48, 32, 48)
        }
        statusView = TextView(this).apply { textSize = 18f }
        root.addView(statusView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val button = Button(this).apply {
            text = "Передать в Пятёрочку"
            setOnClickListener { forwardNow() }
        }
        root.addView(button, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        setContentView(root)
        inspect()
    }

    private fun inspect() {
        val uri = callbackUri
        if (uri == null) {
            statusView.text = "X5 callback не получен"
            return
        }

        val lines = mutableListOf<String>()
        lines += "X5 callback получен"
        lines += "scheme=" + (uri.scheme ?: "")
        lines += "host=" + (uri.host ?: "")

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
                lines += "virtual user=" + user.id + ", handlers=" + candidates.size

                for (candidate in candidates) {
                    val info = candidate.activityInfo ?: continue
                    lines += "• " + info.packageName + "/" + info.name
                    if (targetComponent == null && info.packageName == "ru.pyaterochka.app.browser") {
                        targetUserId = user.id
                        targetComponent = ComponentName(info.packageName, info.name)
                    }
                }
            }
        } catch (t: Throwable) {
            lines += "Ошибка поиска: " + t.javaClass.simpleName + ": " + (t.message ?: "")
            Log.w("X5Redirect", "inspect failed", t)
        }

        if (targetComponent == null) lines += "Обработчик Пятёрочки НЕ найден"
        else lines += "Выбран: " + targetComponent!!.flattenToShortString()
        statusView.text = lines.joinToString("\n")
    }

    private fun forwardNow() {
        val uri = callbackUri ?: return
        val userId = targetUserId
        val component = targetComponent
        if (userId == null || component == null) {
            statusView.append("\nПередача невозможна: нет target Activity")
            return
        }

        try {
            val forward = Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

            BlackBoxCore.getBActivityManager().startActivity(forward, userId)
            statusView.append("\nIntent передан. Если Пятёрочка не открылась — проблема уже внутри guest lifecycle/AppAuth.")
        } catch (t: Throwable) {
            statusView.append("\nОшибка передачи: " + t.javaClass.simpleName + ": " + (t.message ?: ""))
            Log.w("X5Redirect", "forward failed", t)
        }
    }
}
