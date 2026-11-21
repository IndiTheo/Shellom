package com.indidevs.android.shellom.test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.indidevs.android.shellom.test.theme.AppTheme
import com.indidevs.android.shellom.test.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { Surface { MainScreen(this) } } }
    }
}
