package com.picfix.tools

import android.app.Application
import android.content.res.Configuration
import android.content.res.Resources
import com.picfix.tools.config.Constant
import com.picfix.tools.controller.RetrofitServiceManager
import com.picfix.tools.utils.AppUtil
import com.picfix.tools.utils.RomUtil
import com.tencent.bugly.Bugly
import com.tencent.mmkv.MMKV


class BaseApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initData()
        initRom()
        initHttpRequest()
        initMMKV()
        initBugly()
    }

    private fun initData() {
        if (AppUtil.isDebugger(this)) {
            Constant.isDebug = true
        }
    }

    /**
     * 读取设备信息
     *
     */
    private fun initRom() {
        val name = RomUtil.getName()
        if (name != null) {
            Constant.ROM = name
        } else {
            Constant.ROM = Constant.ROM_OTHER
        }
    }

    private fun initHttpRequest() {
        RetrofitServiceManager.getInstance().initRetrofitService()
    }


    /**
     * init mmkv
     */
    private fun initMMKV() {
        MMKV.initialize(this)
    }



    private fun initBugly() {
        Bugly.init(applicationContext, Constant.BUGLY_APPID, false)
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        if (newConfig.fontScale != 1.0f) {
            resources
        }
        super.onConfigurationChanged(newConfig)
    }

    override fun getResources(): Resources {
        val res = super.getResources()
        val config = Configuration()
        config.setToDefaults()
        res.updateConfiguration(config, res.displayMetrics)
        return res
    }

}