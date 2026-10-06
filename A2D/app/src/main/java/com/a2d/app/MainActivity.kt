package com.a2d.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.a2d.app.ui.home.HelloWorldScreen
import com.a2d.app.ui.theme.A2DTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            A2DTheme {
                HelloWorldScreen()
            }
        }
    }
}
