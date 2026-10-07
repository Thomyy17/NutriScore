package com.example.data

import android.graphics.Bitmap
import com.example.util.BitmapUtils

data class SampleFoodItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val expectedType: String,
    val ingredients: String,
    val nutrients: List<Pair<String, String>>
) {
    fun createBitmap(): Bitmap {
        return BitmapUtils.createSampleLabelBitmap(
            title = name,
            ingredients = ingredients,
            nutrients = nutrients
        )
    }
}

object SampleFoodData {
    val items = listOf(
        SampleFoodItem(
            id = "oats",
            name = "Bio Ovesné vločky s ořechy a semínky",
            subtitle = "Přírodní celozrnný produkt bez přidaného cukru",
            expectedType = "Zdravé (Nutri-Score A)",
            ingredients = "Celozrnné ovesné vločky 75%, pražené lískové ořechy 10%, lněná semínka 7%, slunečnicová semínka 5%, chia semínka (Salvia hispanica) 3%. Může obsahovat stopy jiných skořápkových plodů a sezamu.",
            nutrients = listOf(
                "Energetická hodnota" to "1650 kJ / 393 kcal",
                "Tuky" to "12.4 g",
                "  z toho nasycené mastné kyseliny" to "1.6 g",
                "Sacharidy" to "53.2 g",
                "  z toho cukry" to "1.8 g",
                "Vláknina" to "11.6 g",
                "Bílkoviny" to "14.2 g",
                "Sůl" to "0.02 g"
            )
        ),
        SampleFoodItem(
            id = "choco_bar",
            name = "Karamelová čokoládová tyčinka",
            subtitle = "Průmyslová cukrovinka s palmovým tukem a sirupem",
            expectedType = "Nezdravé (Nutri-Score E)",
            ingredients = "Cukr, glukózovo-fruktózový sirup, palmový tuk, kakaové máslo, sušené odstředěné mléko, kakaová hmota, sušená syrovátka (z mléka), pšeničná mouka, mléčný tuk, emulgátory (sójový lecitin E322, polyglycerolpolyricinoleát E476), aromata, jedlá sůl, kypřící látka (hydrogenuhličitan sodný E500).",
            nutrients = listOf(
                "Energetická hodnota" to "2180 kJ / 522 kcal",
                "Tuky" to "28.5 g",
                "  z toho nasycené mastné kyseliny" to "16.8 g",
                "Sacharidy" to "61.0 g",
                "  z toho cukry" to "51.4 g",
                "Vláknina" to "1.4 g",
                "Bílkoviny" to "4.8 g",
                "Sůl" to "0.55 g"
            )
        ),
        SampleFoodItem(
            id = "pate",
            name = "Jemná masová paštika",
            subtitle = "Konzerva s dusitanem sodným a stabilizátory",
            expectedType = "S výhradami (Nutri-Score D)",
            ingredients = "Vepřové sádlo, vepřové maso 25%, voda, vepřová játra 18%, kůže, modifikovaný škrob E1422, solicí směs (jedlá sůl, konzervant: dusitan sodný E250), stabilizátory (difosforečnany E450, trifosforečnany E451), zvýrazňovač chuti (glutaman sodný E621), směs koření, antioxidant (kyselina askorbová E300).",
            nutrients = listOf(
                "Energetická hodnota" to "1380 kJ / 334 kcal",
                "Tuky" to "31.0 g",
                "  z toho nasycené mastné kyseliny" to "11.5 g",
                "Sacharidy" to "3.8 g",
                "  z toho cukry" to "0.5 g",
                "Bílkoviny" to "9.5 g",
                "Sůl" to "1.9 g"
            )
        )
    )
}
