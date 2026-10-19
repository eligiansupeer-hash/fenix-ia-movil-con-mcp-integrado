package com.fenix.mobilemcp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun MarkdownTextRenderer(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    // Split by code blocks
    val parts = text.split(Regex("```"))
    
    Column(modifier = modifier) {
        for (i in parts.indices) {
            val part = parts[i]
            if (i % 2 == 1) {
                // Code block
                // Sometimes the code block starts with the language name, e.g., ```kotlin\ncode...
                // We can optionally strip the first line if it's just the language identifier without spaces
                val codeContent = part.replaceFirst(Regex("^[a-zA-Z0-9_]+\\n"), "").trim()
                
                Text(
                    text = codeContent,
                    color = Color.LightGray,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E1E))
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState())
                )
            } else {
                // Regular text (with inline markdown)
                if (part.isNotEmpty()) {
                    Text(
                        text = parseInlineMarkdown(part),
                        color = color,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        // Simple parser for **bold**, *italic*, `code`
        val pattern = Regex("\\*\\*(.*?)\\*\\*|\\*(.*?)\\*|`(.*?)`")
        val matches = pattern.findAll(text)
        
        for (match in matches) {
            // Append text before the match
            if (currentIndex < match.range.first) {
                append(text.substring(currentIndex, match.range.first))
            }
            
            // Apply style based on the group matched
            when {
                match.groups[1] != null -> {
                    // Bold
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(match.groups[1]!!.value)
                    }
                }
                match.groups[2] != null -> {
                    // Italic
                    withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(match.groups[2]!!.value)
                    }
                }
                match.groups[3] != null -> {
                    // Inline code
                    withStyle(style = SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0x33888888)
                    )) {
                        append(match.groups[3]!!.value)
                    }
                }
            }
            currentIndex = match.range.last + 1
        }
        
        // Append remaining text
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}
