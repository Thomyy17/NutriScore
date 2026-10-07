package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AnalysisUiState
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.screens.AnalysisLoadingScreen
import com.example.ui.screens.AnalysisResultScreen
import com.example.ui.screens.CameraScanScreen
import com.example.ui.screens.ScanHistoryScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NutriCheckApp()
                }
            }
        }
    }
}

@Composable
fun NutriCheckApp(
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Crossfade(
        targetState = state.screen,
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            AppScreen.Camera -> {
                CameraScanScreen(
                    onPhotoCaptured = { bitmap ->
                        viewModel.onPhotoCaptured(bitmap)
                    },
                    onSampleSelected = { sample ->
                        viewModel.onSampleSelected(sample)
                    },
                    onNavigateToHistory = {
                        viewModel.navigateToHistory()
                    },
                    hasHistory = state.history.isNotEmpty(),
                    modifier = Modifier.fillMaxSize()
                )
            }
            AppScreen.Analyzing -> {
                val statusMessage = (state.analysisState as? AnalysisUiState.Loading)?.stage
                    ?: "Gemini AI vyhodnocuje složení..."
                AnalysisLoadingScreen(
                    bitmap = state.capturedBitmap,
                    statusMessage = statusMessage,
                    onCancel = {
                        viewModel.onScanAgain()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            AppScreen.Result -> {
                AnalysisResultScreen(
                    state = state.analysisState,
                    capturedBitmap = state.capturedBitmap,
                    followUpQuestions = state.followUpQuestions,
                    isAskingFollowUp = state.isAskingFollowUp,
                    onAskFollowUp = { question ->
                        viewModel.askFollowUp(question)
                    },
                    onScanAgain = {
                        viewModel.onScanAgain()
                    },
                    onRetry = {
                        viewModel.retryAnalysis()
                    },
                    onNavigateToHistory = {
                        viewModel.navigateToHistory()
                    },
                    onBack = {
                        viewModel.navigateBack()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            AppScreen.History -> {
                ScanHistoryScreen(
                    history = state.history,
                    onSelectItem = { item ->
                        viewModel.viewHistoryItem(item)
                    },
                    onBack = {
                        viewModel.navigateBack()
                    },
                    onScanNew = {
                        viewModel.onScanAgain()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
