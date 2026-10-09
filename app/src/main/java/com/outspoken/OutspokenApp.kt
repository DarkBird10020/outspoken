package com.outspoken

import android.app.Application
import android.os.Build
import com.outspoken.log.AppLog
import com.outspoken.log.FileLog

class OutspokenApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val log = FileLog(getExternalFilesDir(null))
        log.installCrashHandler()
        AppLog.sink = log
        AppLog.write("app", "start on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
        AppLog.write("app", "log file ${log.file?.absolutePath}")
    }
}
