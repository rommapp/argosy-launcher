package com.nendo.argosy.hardware

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

private const val TAG = "FocusDirector"

class FocusDirectorActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Launched on display ${display?.displayId}")
    }

    override fun onResume() {
        super.onResume()
        window.decorView.postDelayed({ finish() }, 50)
    }

    companion object {
        fun launchOnDisplay(context: Context, displayId: Int) {
            val intent = Intent(context, FocusDirectorActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val options = ActivityOptions.makeBasic()
                .setLaunchDisplayId(displayId)
                .toBundle()
            Log.d(TAG, "Directing focus to display $displayId")
            context.startActivity(intent, options)
        }
    }
}
