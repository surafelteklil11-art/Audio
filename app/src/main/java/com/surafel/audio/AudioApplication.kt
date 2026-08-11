package com.surafel.audio

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.content.Intent
import android.widget.TextView

/** Keeps the existing MainActivity search wiring intact while upgrading the UI to SearchActivity. */
class AudioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is MainActivity) {
                    activity.findViewById<TextView>(R.id.searchButton)?.setOnClickListener {
                        activity.startActivity(Intent(activity, SearchActivity::class.java))
                    }
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
