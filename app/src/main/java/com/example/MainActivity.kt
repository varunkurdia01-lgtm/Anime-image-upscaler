package com.example
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val appViewModel: AppViewModel = viewModel()
      val themeIndex by appViewModel.themeIndex.collectAsState()
      val isDark = when (themeIndex) {
          1 -> false
          2 -> true
          else -> isSystemInDarkTheme()
      }
      MyApplicationTheme(themeIndex = themeIndex) {
        PixelBoostApp(viewModel = appViewModel)
      }
    }
  }
}