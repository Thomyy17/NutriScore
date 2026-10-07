package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NutriScoreBadge(
    grade: String,
    modifier: Modifier = Modifier
) {
    val grades = listOf("A", "B", "C", "D", "E")
    val gradeColors = listOf(
        Color(0xFF038141), // A - Dark Green
        Color(0xFF85BB2F), // B - Light Green
        Color(0xFFFECB02), // C - Yellow
        Color(0xFFEE8100), // D - Orange
        Color(0xFFE63E11)  // E - Red
    )

    val activeGrade = grade.trim().uppercase()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        grades.forEachIndexed { index, letter ->
            val isSelected = letter == activeGrade
            val bgColor = if (isSelected) gradeColors[index] else gradeColors[index].copy(alpha = 0.35f)
            val textColor = if (isSelected) Color.White else Color.Black.copy(alpha = 0.6f)
            val size = if (isSelected) 36.dp else 26.dp
            val fontSize = if (isSelected) 18.sp else 12.sp

            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter,
                    color = textColor,
                    fontSize = fontSize,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold
                )
            }
        }
    }
}
