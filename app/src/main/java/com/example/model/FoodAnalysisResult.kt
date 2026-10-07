package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class FoodAnalysisResult(
    val productName: String = "Naskenovaný produkt",
    val healthScore: Int = 60, // 0 - 100
    val verdict: String = "MODERATE", // HEALTHY, MODERATE, UNHEALTHY, NOT_FOOD
    val verdictTitle: String = "S výhradami",
    val summary: String = "",
    val nutriScore: String = "C", // A, B, C, D, E
    val positiveIngredients: List<String> = emptyList(),
    val concerningIngredients: List<IngredientConcern> = emptyList(),
    val additives: List<AdditiveInfo> = emptyList(),
    val recommendation: String = "",
    val healthierAlternative: String = "",
    val suitableForChildren: Boolean = true,
    val childrenSuitabilityVerdict: String = "Vhodné pro děti",
    val childrenSuitabilityReason: String = "",
    val rawIngredientsText: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class IngredientConcern(
    val name: String,
    val reason: String,
    val riskLevel: String = "MEDIUM" // LOW, MEDIUM, HIGH
)

@Serializable
data class AdditiveInfo(
    val code: String, // e.g. "E250"
    val name: String, // e.g. "Dusitan sodný"
    val purpose: String = "", // e.g. "Konzervant"
    val safetyNote: String = "",
    val risk: String = "CAUTION", // SAFE, CAUTION, HARMFUL
    val healthEffects: String = "" // Co konkrétně způsobuje (např. hyperaktivita, zažívací potíže, alergie)
)

enum class HealthVerdict {
    HEALTHY,
    MODERATE,
    UNHEALTHY,
    NOT_FOOD
}
