package com.bsoft.compose.bmusic.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bsoft.compose.bmusic.ui.theme.BMusicTheme
import kotlinx.coroutines.launch
import java.text.Normalizer
import kotlin.math.abs

data class DraggingState(val dragging: Boolean = false, val index: Int = -1)

/**
 * Section letter for the A–Z sidebar. Latin letters (accents stripped) map to A–Z; anything else
 * (digits, symbols, empty titles, non-Latin scripts) is grouped under '#'. Never throws.
 */
fun sectionLetter(text: String?): Char {
    val first = text?.trimStart()?.firstOrNull() ?: return '#'
    val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).firstOrNull() ?: return '#'
    val upper = base.uppercaseChar()
    return if (upper in 'A'..'Z') upper else '#'
}

@Composable
fun <T> AlphabeticList(
    modifier: Modifier = Modifier,
    items: List<T>,
    header: (T) -> Char,
    key: ((T) -> Any)? = null,
    /** Hide the A–Z sidebar for orders that are not alphabetical (date, duration, track). */
    showIndex: Boolean = true,
    build: @Composable (index: Int, item: T) -> Unit
) {
    val scope = rememberCoroutineScope()
    // Recomputed whenever the list changes (initial load, search, MediaStore refresh).
    val headers = remember(items) { items.map { header(it).uppercase() }.distinct() }

    var draggingState by remember { mutableStateOf(DraggingState()) }
    val listState = rememberLazyListState()
    val offsets = remember(headers) { mutableStateMapOf<Int, Float>() }
    fun updateSelectedIndexIfNeeded(offset: Float) {
        val index = offsets.mapValues { abs(it.value - offset) }.entries.minByOrNull { it.value }?.key ?: return
        if (draggingState.index == index) return
        val target = headers.getOrNull(index) ?: return
        draggingState = draggingState.copy(index = index)
        val selectedItemIndex = items.indexOfFirst { header(it).uppercase() == target }
        if (selectedItemIndex < 0) return
        scope.launch {
            listState.scrollToItem(selectedItemIndex)
        }
    }

    val itemKey = key
    Box(modifier = modifier) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (itemKey != null) {
                itemsIndexed(items, key = { _, item -> itemKey(item) }) { index, item -> build(index, item) }
            } else {
                itemsIndexed(items) { index, item -> build(index, item) }
            }
        }

        if (showIndex) Column(verticalArrangement = Arrangement.SpaceAround, horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(horizontal = 4.dp).fillMaxHeight().align(alignment = Alignment.TopEnd)
                //.background(color = MaterialTheme.colorScheme.surface)
                .pointerInput(headers, items) {
                    detectTapGestures {
                        updateSelectedIndexIfNeeded(it.y)
                    }
                }.pointerInput(headers, items){
                    detectDragGestures(
                        onDragStart = {
                            draggingState = draggingState.copy(dragging = true)
                        },
                        onDragEnd = {
                            draggingState = draggingState.copy(dragging = false)
                        },
                        onDrag = { change, _ -> updateSelectedIndexIfNeeded(change.position.y) }
                    )
                }
        ) {
            headers.forEachIndexed { i, header ->
                val dragged = draggingState.dragging && draggingState.index == i
                Row(modifier = Modifier.onGloballyPositioned { offsets[i] = it.boundsInParent().center.y },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if(dragged){
                        Surface(modifier = Modifier.width(80.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) {
                            Text(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), text = header, textAlign = TextAlign.Center)
                        }
                    }
                    Text(
                        header,
                        color = Color.Transparent,
                        fontWeight = if(dragged) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if(dragged) 16.sp else 12.sp,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun AlphabeticListPreview() {
    val items = remember { LoremIpsum().values.first().split(" ").filter { !it.first().isWhitespace() }.sortedBy { it.lowercase() } }

    BMusicTheme {
        Surface {
            AlphabeticList(items = items, header = { sectionLetter(it) }){ _, item ->
                Text(item, fontSize = 18.sp, fontWeight = FontWeight.Light, letterSpacing = 1.4.sp)
            }
        }
    }
}