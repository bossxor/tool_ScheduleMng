package com.bossxor.harugyeol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = (application as HarugyeolApp).store
        setContent {
            MaterialTheme {
                LaunchedEffect(Unit) { store.update { it.copy(launchCount = it.launchCount + 1) } }
                val data by store.state.collectAsState()
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("하루결", style = MaterialTheme.typography.headlineLarge)
                    Text("v${BuildConfig.VERSION_NAME} · 실행 ${data.launchCount}회")
                }
            }
        }
    }
}
