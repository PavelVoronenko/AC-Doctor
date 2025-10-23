package com.antago30.acdoctor

import android.app.Application
import android.util.Log
import com.polidea.rxandroidble2.RxBleClient
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins

class BleApplication : Application() {
    companion object {
        lateinit var instance: BleApplication private set
    }

    lateinit var rxBleClient: RxBleClient

    override fun onCreate() {
        super.onCreate()
        instance = this
        rxBleClient = RxBleClient.create(this)

        RxJavaPlugins.setErrorHandler { throwable ->
            if (throwable is UndeliverableException) {
                Log.w("RxJava", "Undeliverable exception", throwable)
            } else {
                Thread.currentThread().uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), throwable)
            }
        }
    }
}