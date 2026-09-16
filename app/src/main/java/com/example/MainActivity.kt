package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.igboarpg.presentation.ArpgEngineViewModel
import com.example.igboarpg.presentation.IgboArpgTheme
import com.example.igboarpg.presentation.MainScaffold

class MainActivity : ComponentActivity() {
  private val viewModel: ArpgEngineViewModel by viewModels {
    ArpgEngineViewModel.provideFactory(application)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      IgboArpgTheme {
        MainScaffold(
          viewModel = viewModel,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }
}
