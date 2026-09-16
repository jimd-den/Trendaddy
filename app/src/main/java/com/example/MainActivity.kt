package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.stratum.app.StratumApp
import com.stratum.content.igbo.IgboContentPack
import com.stratum.core.designsystem.theme.StratumTheme
import com.example.igboarpg.presentation.ArpgEngineViewModel
import com.example.igboarpg.presentation.MainScaffold

class MainActivity : ComponentActivity() {

  // The creator studio still runs on the original view model. It is reached from
  // the new shell and will move into its own feature module as those screens are
  // ported.
  private val studioViewModel: ArpgEngineViewModel by viewModels {
    ArpgEngineViewModel.provideFactory(application)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      StratumTheme(palette = IgboContentPack.palette) {
        StratumApp(
          modifier = Modifier.fillMaxSize(),
          studioContent = {
            MainScaffold(
              viewModel = studioViewModel,
              modifier = Modifier.fillMaxSize(),
            )
          },
        )
      }
    }
  }
}
