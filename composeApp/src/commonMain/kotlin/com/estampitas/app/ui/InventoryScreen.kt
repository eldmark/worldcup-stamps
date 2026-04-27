package com.estampitas.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.estampitas.presentation.EstampitasUiState
import com.estampitas.presentation.StickerLineUi
import com.estampitas.presentation.StickerQuantityBucket

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InventoryScreen(
    ui: EstampitasUiState,
    onPlusOne: (Int) -> Unit,
    onMinusOne: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ui.isLoading && ui.stickers.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ui.stickers, key = { it.id }) { line ->
            StickerRow(
                line = line,
                onPlusOne = { onPlusOne(line.id) },
                onMinusOne = { onMinusOne(line.id) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerRow(
    line: StickerLineUi,
    onPlusOne: () -> Unit,
    onMinusOne: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 72f

    val containerColor =
        when (line.bucket) {
            StickerQuantityBucket.Faltante -> MaterialTheme.colorScheme.errorContainer
            StickerQuantityBucket.Normal -> MaterialTheme.colorScheme.surfaceVariant
            StickerQuantityBucket.Duplicada -> MaterialTheme.colorScheme.tertiaryContainer
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            accumulatedDrag += dragAmount
                        },
                        onDragCancel = { accumulatedDrag = 0f },
                        onDragEnd = {
                            if (accumulatedDrag > swipeThreshold) {
                                onMinusOne()
                            }
                            accumulatedDrag = 0f
                        },
                    )
                }
                .combinedClickable(
                    onClick = onPlusOne,
                    onLongClick = onPlusOne,
                ),
        colors =
            CardDefaults.cardColors(
                containerColor = containerColor,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(line.code, style = MaterialTheme.typography.titleMedium)
                val subtitle = listOfNotNull(line.team, line.playerName).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    when (line.bucket) {
                        StickerQuantityBucket.Faltante -> "Faltante"
                        StickerQuantityBucket.Normal -> "Normal"
                        StickerQuantityBucket.Duplicada -> "Duplicada"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = line.quantity.toString(),
                style = MaterialTheme.typography.headlineSmall,
                modifier =
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.medium,
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
