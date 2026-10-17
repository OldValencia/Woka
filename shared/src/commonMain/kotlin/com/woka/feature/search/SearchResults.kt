package com.woka.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.components.FolderCardPreview
import com.woka.core.ui.components.SegmentedControl
import com.woka.core.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

private const val THIS_FOLDER = "This Folder"
private const val ALL_FOLDERS = "All Folders"

/**
 * Search results in place of the grid (SIDE-008), while the toolbar's box holds a
 * query: the scope, a count, and the matches as tiles with the name and its place.
 * Esc or an empty box brings the folder back — it was never left, only covered.
 *
 * Searched 200 ms after the last key press, off the main thread, and abandoned the
 * moment the query changes again.
 */
@Composable
fun SearchResults(currentPath: String?, roots: List<String>, onOpen: (File) -> Unit) {
    val query = SearchState.query
    val everywhere = SearchState.everywhere || currentPath == null
    val scope = if (everywhere) roots else listOfNotNull(currentPath)
    var hits by remember { mutableStateOf<List<SearchHit>?>(null) }
    LaunchedEffect(query, scope) {
        hits = null
        delay(200.milliseconds)
        hits = withContext(Dispatchers.IO) { searchLibrary(query, scope, roots) { isActive } }
    }

    Column(modifier = Modifier.fillMaxSize().background(Theme.colors.background)) {
        Row(
            modifier = Modifier.padding(start = 40.dp, end = 40.dp, top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (currentPath != null) {
                Box(modifier = Modifier.width(220.dp)) {
                    SegmentedControl(
                        listOf(THIS_FOLDER, ALL_FOLDERS),
                        if (everywhere) ALL_FOLDERS else THIS_FOLDER,
                        onSelect = { SearchState.everywhere = it == ALL_FOLDERS }
                    )
                }
            }
            val found = hits
            BasicText(
                if (found == null) "Searching…" else "${found.size} results for “$query”",
                style = TextStyle(color = Theme.colors.textSecondary, fontSize = 13.sp)
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(hits.orEmpty(), key = { it.file.absolutePath }) { hit -> HitCell(hit, query, onOpen) }
        }
    }
}

@Composable
private fun HitCell(hit: SearchHit, query: String, onOpen: (File) -> Unit) {
    Column(
        modifier = Modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onOpen(hit.file) }
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))) {
            FolderCardPreview(hit.file.isDirectory, hit.file, emptyList(), Modifier.fillMaxSize())
        }
        BasicText(
            highlighted(hit.file.name, query),
            modifier = Modifier.padding(start = 2.dp, top = 6.dp),
            maxLines = 1,
            style = TextStyle(color = Theme.colors.textPrimary, fontSize = 12.sp)
        )
        BasicText(
            hit.place,
            modifier = Modifier.padding(start = 2.dp, top = 1.dp),
            maxLines = 1,
            style = TextStyle(color = Theme.colors.textTertiary, fontSize = 11.sp)
        )
    }
}

/** The name with the matched run marked, as `.rn mark` does: a yellow wash, not bold. */
private fun highlighted(name: String, query: String): AnnotatedString = buildAnnotatedString {
    append(name)
    val at = name.indexOf(query.trim(), ignoreCase = true)
    if (at >= 0 && query.isNotBlank()) {
        addStyle(SpanStyle(background = MATCH_WASH), at, at + query.trim().length)
    }
}

private val MATCH_WASH = Color(0x59FFD60A)
