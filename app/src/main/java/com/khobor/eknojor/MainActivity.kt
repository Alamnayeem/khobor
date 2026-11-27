package com.khobor.eknojor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme { MainScreen(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshIfStale()
    }
}
