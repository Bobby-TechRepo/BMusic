package com.bsoft.compose.bmusic.ui.components

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.ui.theme.BMusicTheme
import com.bsoft.compose.bmusic.utils.toTimeFormat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device tests for the shared song row: `./gradlew :app:connectedDebugAndroidTest`. */
@RunWith(AndroidJUnit4::class)
class SongRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val song = Song(id = 7, displayName = "track.mp3", title = "Midnight Drive", artist = "The Arcs", album = "Night", duration = 185_000)

    @Test
    fun showsTitleAndArtistWithDuration() {
        rule.setContent { BMusicTheme { SongRow(song = song, onClick = {}) } }

        rule.onNodeWithText("Midnight Drive").assertExists()
        rule.onNodeWithText("The Arcs · ${185_000L.toTimeFormat()}", useUnmergedTree = true).assertExists()
    }

    @Test
    fun tappingTheRowAndTheMenuCallTheirHandlers() {
        var taps = 0
        var menus = 0
        rule.setContent { BMusicTheme { SongRow(song = song, onMenu = { menus++ }, onClick = { taps++ }) } }

        rule.onNodeWithTag(TestTags.SONG_ROW).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.cd_song_options)).performClick()

        assertEquals(1, taps)
        assertEquals(1, menus)
    }

    @Test
    fun selectionModeHidesTheMenuAndReportsSelection() {
        rule.setContent {
            BMusicTheme { SongRow(song = song, selectionMode = true, selected = true, onMenu = {}, onClick = {}) }
        }

        rule.onNodeWithContentDescription(context.getString(R.string.cd_song_options)).assertDoesNotExist()
        rule.onNodeWithTag(TestTags.SONG_ROW).assertIsSelected()
    }

    @Test
    fun unselectedRowInSelectionModeIsNotSelected() {
        rule.setContent { BMusicTheme { SongRow(song = song, selectionMode = true, selected = false, onClick = {}) } }

        rule.onNodeWithTag(TestTags.SONG_ROW).assertIsNotSelected()
    }

    @Test
    fun queueRowShowsRemoveAndDragHandle() {
        var removed = false
        rule.setContent {
            BMusicTheme { SongRow(song = song, showDragHandle = true, onRemove = { removed = true }, onClick = {}) }
        }

        rule.onNodeWithContentDescription(context.getString(R.string.cd_reorder), useUnmergedTree = true).assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.cd_remove_from_queue)).performClick()
        assertEquals(true, removed)
    }

    @Test
    fun noDragHandleUnlessAsked() {
        rule.setContent { BMusicTheme { SongRow(song = song, onClick = {}) } }

        rule.onNodeWithContentDescription(context.getString(R.string.cd_reorder), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun albumListsShowTheTrackNumber() {
        rule.setContent { BMusicTheme { SongRow(song = song, trackNumber = 4, onClick = {}) } }

        rule.onNodeWithText("4", useUnmergedTree = true).assertExists()
    }
}
