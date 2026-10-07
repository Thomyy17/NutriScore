package com.example.data.gemini

import android.graphics.Bitmap
import android.util.Log
import com.example.BuildConfig
import com.example.model.AdditiveInfo
import com.example.model.FoodAnalysisResult
import com.example.model.IngredientConcern
import com.example.util.BitmapUtils
import com.example.util.BitmapUtils.toBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GeminiRepository(
    private val apiService: GeminiApiService = RetrofitClient.service
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun analyzeFoodImage(bitmap: Bitmap): Result<FoodAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Klíč GEMINI_API_KEY není nastaven v AI Studio Secrets panelu. Nastavte prosím platný Gemini API klíč."
                    )
                )
            }

            val scaledBitmap = BitmapUtils.scaleDown(bitmap, 1280)
            val base64Image = scaledBitmap.toBase64(85)

            val prompt = """
                Jsi expertní nutriční specialista a certifikovaný biochemik specializující se na analýzu složení potravin a aditiv.
                Tvým úkolem je analyzovat přiloženou fotografii složení potraviny (ingredience a případně nutriční tabulku).
                
                Detailně zkontroluj:
                1. Všechny ingredience (pořadí určuje množství v potravině).
                2. Detekuj rizikové látky: přidaný cukr (glukózo-fruktózový sirup, maltodextrin), palmový tuk, ztužené tuky, transmastné kyseliny, nadbytek soli.
                3. Identifikuj všechna éčka (aditiva) s kódy E-XXX, popiš jejich bezpečnost (zda jsou bezpečná, podezřelá nebo škodlivá).
                4. Zhodnoť celkovou zdravost na škále 0-100 a stanov Nutri-Score (A-E).
                5. Pokud obrázek NEOBSAHUJE složení potraviny ani potravinový obal, nastav verdict na "NOT_FOOD".
                
                Odpověz POUZE validním JSON objektem v přesně tomto schématu bez jakéhokoliv dalšího textu okolo:
                {
                  "productName": "Název výrobku (nebo odhad)",
                  "healthScore": 75,
                  "verdict": "HEALTHY",
                  "verdictTitle": "Zdravá volba",
                  "summary": "Stručné, jasné a srozumitelné shrnutí pro spotřebitele v češtině (2-3 věty), zda je potravina zdravá a proč.",
                  "nutriScore": "B",
                  "positiveIngredients": ["Ovesné vločky (vysoký obsah vlákniny)", "Lískové ořechy"],
                  "concerningIngredients": [
                    {
                      "name": "Glukózový sirup",
                      "reason": "Rychlý cukr způsobující výkyvy glykémie",
                      "riskLevel": "HIGH"
                    }
                  ],
                  "additives": [
                    {
                      "code": "E322",
                      "name": "Sójový lecitin",
                      "purpose": "Emulgátor",
                      "safetyNote": "Přírodní původ, bezpečný",
                      "risk": "SAFE"
                    }
                  ],
                  "recommendation": "Vhodné pro běžnou konzumaci jako součást pestré stravy.",
                  "healthierAlternative": "Vyzkoušejte čisté ovesné vločky s čerstvým ovocem bez přidaného sirupu.",
                  "rawIngredientsText": "Celé detekované složení z etikety..."
                }
                
                Hodnoty pro 'verdict':
                - "HEALTHY" (skóre 75-100)
                - "MODERATE" (skóre 50-74)
                - "UNHEALTHY" (skóre 0-49)
                - "NOT_FOOD" (pokud na fotce není potravina ani složení)
                
                Hodnoty pro 'riskLevel': "LOW", "MEDIUM", "HIGH"
                Hodnoty pro 'risk' u aditiv: "SAFE", "CAUTION", "HARMFUL"
            """.trimIndent()

            val request = GenerateContentRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(text = prompt),
                            Part(
                                inlineData = InlineData(
                                    mimeType = "image/jpeg",
                                    data = base64Image
                                )
                            )
                        )
                    )
                ),
                generationConfig = GenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.2f
                )
            )

            val response = apiService.generateContent(apiKey, request)
            val rawText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return@withContext Result.failure(IllegalStateException("Gemini nevrátil žádnou odpověď."))

            val cleanedJson = cleanJsonString(rawText)
            val parsedResult = json.decodeFromString<FoodAnalysisResult>(cleanedJson)
            Result.success(parsedResult)
        } catch (e: Exception) {
            Log.e("GeminiRepository", "Error analyzing food image", e)
            Result.failure(e)
        }
    }

    suspend fun askFollowUpQuestion(
        analysis: FoodAnalysisResult,
        question: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(IllegalStateException("Chybí Gemini API klíč."))
            }

            val prompt = """
                Jsi nutriční poradce. Uživatel naskenoval potravinu s tímto profilem:
                - Produkt: ${analysis.productName}
                - Skóre zdravosti: ${analysis.healthScore}/100
                - Nutri-Score: ${analysis.nutriScore}
                - Složení: ${analysis.rawIngredientsText}
                - Pozitiva: ${analysis.positiveIngredients.joinToString(", ")}
                - Rizika: ${analysis.concerningIngredients.joinToString { it.name + " (" + it.reason + ")" }}
                - Aditiva: ${analysis.additives.joinToString { it.code + " " + it.name }}
                
                Otázka uživatele: "$question"
                
                Odpověz věcně, přátelsky a srozumitelně v češtině (max 2-3 odstavce).
            """.trimIndent()

            val request = GenerateContentRequest(
                contents = listOf(
                    Content(parts = listOf(Part(text = prompt)))
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.7f
                )
            )

            val response = apiService.generateContent(apiKey, request)
            val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Omlouvám se, nepodařilo se vygenerovat odpověď."
            Result.success(text)
        } catch (e: Exception) {
            Log.e("GeminiRepository", "Error asking follow up", e)
            Result.failure(e)
        }
    }

    private fun cleanJsonString(raw: String): String {
        var str = raw.trim()
        if (str.startsWith("```json")) {
            str = str.removePrefix("```json")
        } else if (str.startsWith("```")) {
            str = str.removePrefix("```")
        }
        if (str.endsWith("```")) {
            str = str.removeSuffix("```")
        }
        return str.trim()
    }
}
