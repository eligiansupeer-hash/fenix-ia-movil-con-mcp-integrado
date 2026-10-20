@file:Suppress("FunctionNaming")

package com.fenix.mobilemcp.ui.screens.sandbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fenix.mobilemcp.sandbox.terminal.MAX_COLUMNS
import com.fenix.mobilemcp.sandbox.terminal.MAX_ROWS
import com.fenix.mobilemcp.sandbox.terminal.MIN_COLUMNS
import com.fenix.mobilemcp.sandbox.terminal.MIN_ROWS
import com.fenix.mobilemcp.sandbox.terminal.TerminalSnapshot
import kotlinx.coroutines.delay

private val TerminalStyle =
    TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        color = Color(0xFFE5E5E5),
    )

/**
 * Character-cell renderer. The grid size is derived from the measured advance of
 * the monospace font, not from a fixed column count: the same UI then works on a
 * 360 dp phone and a tablet, and the guest gets a matching PTY winsize.
 */
@Composable
fun TerminalGrid(
    snapshot: TerminalSnapshot,
    onResize: (columns: Int, rows: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.background(Color.Black).clipToBounds(),
    ) {
        val paddingPx = with(density) { 4.dp.toPx() }
        val advance =
            remember(measurer) {
                measurer.measure(AnnotatedString("M"), TerminalStyle).size.width.toFloat().coerceAtLeast(1f)
            }
        val lineStep = with(density) { TerminalStyle.lineHeight.toPx() }.coerceAtLeast(1f)
        val availableWidth = (constraints.maxWidth - paddingPx * 2).coerceAtLeast(1f)
        val availableHeight = (constraints.maxHeight - paddingPx * 2).coerceAtLeast(1f)

        val columns = maxOf(MIN_COLUMNS, minOf(MAX_COLUMNS, (availableWidth / advance).toInt()))
        val rows = maxOf(MIN_ROWS, minOf(MAX_ROWS, (availableHeight / lineStep).toInt()))

        LaunchedEffect(columns, rows) {
            delay(120)
            onResize(columns, rows)
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(4.dp),
        ) {
            val lineHeight = with(density) { TerminalStyle.lineHeight.toDp() }
            repeat(snapshot.rows) { row ->
                BasicText(
                    text = remember(snapshot.revision, row) { rowText(snapshot, row) },
                    style = TerminalStyle,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth().height(lineHeight),
                )
            }
        }
    }
}

private data class CellStyle(
    val fg: Int,
    val bg: Int,
    val bold: Boolean,
)

private fun rowText(
    snapshot: TerminalSnapshot,
    row: Int,
): AnnotatedString = buildAnnotatedString {
    var column = 0
    while (column < snapshot.columns) {
        val first = styleAt(snapshot, column, row)
        val text = StringBuilder()
        var cursor = column
        while (cursor < snapshot.columns && styleAt(snapshot, cursor, row) == first) {
            text.append(snapshot.cellAt(cursor, row).char)
            cursor++
        }
        val color = AnsiPalette.color(first.fg)
        val background = AnsiPalette.color(first.bg)
        withStyle(
            SpanStyle(
                color = color,
                background = background,
                fontWeight = if (first.bold) FontWeight.Bold else FontWeight.Normal,
            ),
        ) {
            append(text)
        }
        column = cursor
    }
}

private fun styleAt(
    snapshot: TerminalSnapshot,
    column: Int,
    row: Int,
): CellStyle {
    val cell = snapshot.cellAt(column, row)
    val isCursor = snapshot.cursorVisible && column == snapshot.cursorCol && row == snapshot.cursorRow
    return if (isCursor) {
        CellStyle(fg = 0, bg = 7, bold = cell.bold)
    } else {
        CellStyle(fg = cell.fg, bg = cell.bg, bold = cell.bold)
    }
}

/** xterm's 16 base colours plus the 6×6×6 cube and 24-step grey ramp. */
object AnsiPalette {
    private val base =
        listOf(
            Color(0xFF000000),
            Color(0xFFCD0000),
            Color(0xFF00CD00),
            Color(0xFFCDCD00),
            Color(0xFF0000EE),
            Color(0xFFCD00CD),
            Color(0xFF00CDCD),
            Color(0xFFE5E5E5),
            Color(0xFF7F7F7F),
            Color(0xFFFF0000),
            Color(0xFF00FF00),
            Color(0xFFFFFF00),
            Color(0xFF5C5CFF),
            Color(0xFFFF00FF),
            Color(0xFF00FFFF),
            Color(0xFFFFFFFF),
        )
    private val levels = intArrayOf(0, 95, 135, 175, 215, 255)

    fun color(index: Int): Color =
        when {
            index in 0..15 -> base[index]
            index in 16..231 -> {
                val value = index - 16
                Color(
                    red = levels[value / 36],
                    green = levels[(value / 6) % 6],
                    blue = levels[value % 6],
                )
            }
            index in 232..255 -> {
                val grey = 8 + (index - 232) * 10
                Color(grey, grey, grey)
            }
            else -> base[7]
        }
}
