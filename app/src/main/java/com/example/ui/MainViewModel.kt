package com.example.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SampleFoodData
import com.example.data.SampleFoodItem
import com.example.data.gemini.GeminiRepository
import com.example.model.FoodAnalysisResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AppScreen {
    data object Camera : AppScreen
    data object Analyzing : AppScreen
    data object Result : AppScreen
    data object History : AppScreen
}

sealed interface AnalysisUiState {
    data object Idle : AnalysisUiState
    data class Loading(val stage: String) : AnalysisUiState
    data class Success(val result: FoodAnalysisResult) : AnalysisUiState
    data class Error(val message: String) : AnalysisUiState
}

data class MainUiState(
    val screen: AppScreen = AppScreen.Camera,
    val capturedBitmap: Bitmap? = null,
    val analysisState: AnalysisUiState = AnalysisUiState.Idle,
    val activeResult: FoodAnalysisResult? = null,
    val history: List<FoodAnalysisResult> = emptyList(),
    val followUpQuestions: List<Pair<String, String>> = emptyList(), // question to answer
    val isAskingFollowUp: Boolean = false,
    val followUpError: String? = null
)

class MainViewModel(
    private val repository: GeminiRepository = GeminiRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun onPhotoCaptured(bitmap: Bitmap) {
        _uiState.update {
            it.copy(
                screen = AppScreen.Analyzing,
                capturedBitmap = bitmap,
                analysisState = AnalysisUiState.Loading("Čtu složení z fotografie a odesílám Gemini AI..."),
                followUpQuestions = emptyList(),
                followUpError = null
            )
        }
        performAnalysis(bitmap)
    }

    fun onSampleSelected(sample: SampleFoodItem) {
        val bitmap = sample.createBitmap()
        onPhotoCaptured(bitmap)
    }

    private fun performAnalysis(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(analysisState = AnalysisUiState.Loading("Gemini AI vyhodnocuje složení a aditiva..."))
            }

            val result = repository.analyzeFoodImage(bitmap)
            result.onSuccess { analysis ->
                _uiState.update { state ->
                    val updatedHistory = listOf(analysis) + state.history.filter { it.timestamp != analysis.timestamp }
                    state.copy(
                        screen = AppScreen.Result,
                        analysisState = AnalysisUiState.Success(analysis),
                        activeResult = analysis,
                        history = updatedHistory
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        screen = AppScreen.Result,
                        analysisState = AnalysisUiState.Error(
                            error.localizedMessage ?: "Nepodařilo se vyhodnotit složení. Zkontrolujte připojení k internetu a platnost Gemini API klíče."
                        )
                    )
                }
            }
        }
    }

    fun askFollowUp(question: String) {
        val currentResult = _uiState.value.activeResult ?: return
        if (question.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAskingFollowUp = true, followUpError = null) }
            val res = repository.askFollowUpQuestion(currentResult, question.trim())
            res.onSuccess { answer ->
                _uiState.update { state ->
                    state.copy(
                        isAskingFollowUp = false,
                        followUpQuestions = state.followUpQuestions + (question to answer)
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        isAskingFollowUp = false,
                        followUpError = err.localizedMessage ?: "Chyba při komunikaci s Gemini"
                    )
                }
            }
        }
    }

    fun retryAnalysis() {
        val bitmap = _uiState.value.capturedBitmap ?: return
        onPhotoCaptured(bitmap)
    }

    fun onScanAgain() {
        _uiState.update {
            it.copy(
                screen = AppScreen.Camera,
                analysisState = AnalysisUiState.Idle,
                followUpQuestions = emptyList(),
                followUpError = null
            )
        }
    }

    fun navigateToHistory() {
        _uiState.update { it.copy(screen = AppScreen.History) }
    }

    fun navigateBack() {
        val current = _uiState.value.screen
        when (current) {
            AppScreen.History -> {
                _uiState.update {
                    it.copy(
                        screen = if (it.activeResult != null) AppScreen.Result else AppScreen.Camera
                    )
                }
            }
            AppScreen.Result -> {
                _uiState.update { it.copy(screen = AppScreen.Camera) }
            }
            AppScreen.Analyzing -> {
                _uiState.update { it.copy(screen = AppScreen.Camera) }
            }
            AppScreen.Camera -> {
                // At root
            }
        }
    }

    fun viewHistoryItem(item: FoodAnalysisResult) {
        _uiState.update {
            it.copy(
                screen = AppScreen.Result,
                activeResult = item,
                analysisState = AnalysisUiState.Success(item),
                followUpQuestions = emptyList()
            )
        }
    }
}
