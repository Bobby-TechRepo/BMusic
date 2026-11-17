package com.bsoft.compose.bmusic.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.ui.theme.BMusicTheme

enum class PlayingPage(val titleRes: Int, val icon: Int){
    Queue(titleRes = R.string.queue, icon = R.drawable.fluent__music_note_2_play_20_regular),
    Playing(titleRes = R.string.playing_tab,  icon =R.drawable.lucide__mic_vocal),
    Lyrics(titleRes = R.string.lyrics, icon = R.drawable.ic_lyrics),
    Equalizer(titleRes = R.string.equalizer, icon = R.drawable.si__equalizer_fill)
}

@Composable
fun PlayingTab(modifier: Modifier = Modifier, active: Int = 1, clicked: (Int)-> Unit = {}){
    val activeStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
    val inActiveStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Light, color = Color.White.copy(alpha = 0.6f))
    
    Row(modifier = modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        PlayingPage.entries.forEachIndexed { index, page ->
            Column(modifier = Modifier.weight(1f).clickable(role = Role.Tab) { clicked(index) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)){
                Image(modifier = Modifier.size(26.dp), imageVector = ImageVector.vectorResource(page.icon),
                    colorFilter = ColorFilter.tint(color = if(active == index) Color.White else Color.White.copy(alpha = 0.6f)),
                    contentDescription = null)
                Text(modifier = Modifier.fillMaxWidth(),
                    text = stringResource(page.titleRes), textAlign = TextAlign.Center, maxLines = 1,
                    style = if(active == index) activeStyle else inActiveStyle)
            }
            if(page != PlayingPage.entries.last()){
                VerticalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

@Preview
@Composable
private fun PlayingTabPreview(){
    BMusicTheme {
        Surface {
            PlayingTab()
        }
    }
}