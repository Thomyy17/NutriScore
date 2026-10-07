package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BitmapUtils {

    fun Bitmap.toBase64(quality: Int = 85): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun scaleDown(bitmap: Bitmap, maxDimension: Int = 1280): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    suspend fun uriToBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Creates a high-fidelity synthetic food label bitmap for demonstration/testing.
     * Perfect for running on emulators where a physical camera is unavailable.
     */
    fun createSampleLabelBitmap(
        title: String,
        ingredients: String,
        nutrients: List<Pair<String, String>>
    ): Bitmap {
        val width = 800
        val height = 950
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.rgb(250, 248, 245))

        val borderPaint = Paint().apply {
            color = Color.rgb(210, 210, 210)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val cardRect = RectF(30f, 30f, (width - 30).toFloat(), (height - 30).toFloat())
        canvas.drawRoundRect(cardRect, 20f, 20f, borderPaint)

        val headerPaint = Paint().apply {
            color = Color.rgb(25, 60, 45)
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.rgb(40, 40, 40)
            textSize = 24f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val boldTextPaint = Paint().apply {
            color = Color.rgb(20, 20, 20)
            textSize = 25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        var currentY = 90f
        canvas.drawText("SLOŽENÍ A VÝŽIVOVÉ ÚDAJE", 60f, currentY, headerPaint)
        currentY += 45f

        canvas.drawText("Název: $title", 60f, currentY, boldTextPaint)
        currentY += 45f

        val separatorPaint = Paint().apply {
            color = Color.rgb(200, 200, 200)
            strokeWidth = 2f
        }
        canvas.drawLine(60f, currentY, 740f, currentY, separatorPaint)
        currentY += 35f

        canvas.drawText("SLOŽENÍ VÝROBKU:", 60f, currentY, boldTextPaint)
        currentY += 35f

        // Wrap ingredients text
        val words = ingredients.split(" ")
        var line = ""
        for (word in words) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            if (textPaint.measureText(testLine) > 680f) {
                canvas.drawText(line, 60f, currentY, textPaint)
                currentY += 32f
                line = word
            } else {
                line = testLine
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, 60f, currentY, textPaint)
            currentY += 45f
        }

        canvas.drawLine(60f, currentY, 740f, currentY, separatorPaint)
        currentY += 35f

        canvas.drawText("VÝŽIVOVÉ HODNOTY NA 100g:", 60f, currentY, boldTextPaint)
        currentY += 35f

        for ((name, value) in nutrients) {
            canvas.drawText(name, 60f, currentY, textPaint)
            val valWidth = boldTextPaint.measureText(value)
            canvas.drawText(value, 740f - valWidth, currentY, boldTextPaint)
            currentY += 32f
        }

        return bitmap
    }
}
