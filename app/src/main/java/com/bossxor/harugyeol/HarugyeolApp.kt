package com.bossxor.harugyeol

import android.app.Application
import java.io.File

class HarugyeolApp : Application() {
    val store: AppStore by lazy { AppStore.open(File(filesDir, "data.json")) }
}
