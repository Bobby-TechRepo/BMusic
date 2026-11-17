package com.bsoft.compose.bmusic.ui.components

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.ui.theme.BMusicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device tests for the bottom-sheet parts and the playlist sheets. */
@RunWith(AndroidJUnit4::class)
class SheetsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test
    fun headerShowsTitleAndSubtitle() {
        rule.setContent {
            BMusicTheme { SheetHeader(title = "Road trip", subtitle = "12 songs") { SheetIconTile(R.drawable.ms_queue_music) } }
        }

        rule.onNodeWithText("Road trip").assertExists()
        rule.onNodeWithText("12 songs").assertExists()
    }

    @Test
    fun sheetActionAndQuickActionAreClickable() {
        var action = 0
        var quick = 0
        rule.setContent {
            BMusicTheme {
                SheetContent {
                    SheetQuickActions {
                        SheetQuickAction(icon = R.drawable.ms_shuffle, label = "Shuffle") { quick++ }
                    }
                    SheetAction(icon = R.drawable.ms_share, label = "Share") { action++ }
                }
            }
        }

        rule.onNodeWithText("Shuffle").performClick()
        rule.onNodeWithText("Share").performClick()
        assertEquals(1, quick)
        assertEquals(1, action)
    }

    @Test
    fun playlistMenuCallsEachHandlerWithThePlaylistId() {
        val calls = mutableListOf<Pair<String, Long>>()
        rule.setContent {
            BMusicTheme {
                PlaylistMenu(
                    playlist = 42,
                    playlistName = "Road trip",
                    play = { calls += "play" to it },
                    shuffle = { calls += "shuffle" to it },
                    rename = { calls += "rename" to it },
                    addSongs = { calls += "add" to it },
                    delete = { calls += "delete" to it }
                )
            }
        }

        rule.onNodeWithText(text(R.string.play)).performClick()
        rule.onNodeWithText(text(R.string.shuffle)).performClick()
        rule.onNodeWithText(text(R.string.add_songs)).performClick()
        rule.onNodeWithText(text(R.string.rename)).performClick()
        rule.onNodeWithText(text(R.string.delete)).performClick()

        assertEquals(
            listOf("play" to 42L, "shuffle" to 42L, "add" to 42L, "rename" to 42L, "delete" to 42L),
            calls
        )
    }

    @Test
    fun playlistMenuHidesOptionalActionsWithoutHandlers() {
        rule.setContent { BMusicTheme { PlaylistMenu(playlist = 1, playlistName = "Mix") } }

        rule.onNodeWithText(text(R.string.open)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.edit)).assertDoesNotExist()
        rule.onNodeWithText(text(R.string.export_m3u)).assertDoesNotExist()
    }

    @Test
    fun playlistFormEnablesCreateOnlyWithAName() {
        var created: String? = null
        rule.setContent { BMusicTheme { PlaylistForm { created = it } } }

        val create = rule.onNodeWithText(text(R.string.btn_create))
        create.assertIsNotEnabled()
        assertNull(created)

        rule.onNode(hasSetTextAction()).performTextInput("  Gym  ")
        create.assertIsEnabled().performClick()
        rule.waitForIdle()

        assertEquals("Gym", created)
    }
}
