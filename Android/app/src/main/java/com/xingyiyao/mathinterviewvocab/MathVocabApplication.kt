package com.xingyiyao.mathinterviewvocab

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.xingyiyao.mathinterviewvocab.audio.SpeechController
import com.xingyiyao.mathinterviewvocab.updates.AndroidUpdater

data class AppServices(val speech: SpeechController, val updater: AndroidUpdater)

class MathVocabApplication : Application() {
    lateinit var services: AppServices
        private set

    override fun onCreate() {
        super.onCreate()
        services = AppServices(SpeechController(this), AndroidUpdater(this))
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { services.updater.onAppForeground() }

            override fun onStop(owner: LifecycleOwner) {
                services.speech.stop()
                services.updater.onAppBackground()
            }
        })
    }
}
