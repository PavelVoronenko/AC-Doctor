package com.antago30.acdoctor

import android.app.Application
import com.polidea.rxandroidble2.RxBleClient

class BleApplication : Application() {
    companion object {
        lateinit var instance: BleApplication private set
    }

    lateinit var rxBleClient: RxBleClient

    override fun onCreate() {
        super.onCreate()
        instance = this
        rxBleClient = RxBleClient.create(this)
    }
}