package com.blissless.anime.ui.screens.character

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.blissless.anime.MainViewModel
import com.blissless.anime.data.models.CharacterData
import com.blissless.anime.data.models.StaffData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterScreen(
    characterId: Int,
    viewModel: MainViewModel,
    isOled: Boolean = false,
    onDismiss: () -> Unit,
    onAnimeClick: (Int) -> Unit
) {
    val context = LocalContext.current
    var character by remember { mutableStateOf<CharacterData?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues()
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues()

    LaunchedEffect(characterId) {
        isLoading = true
        character = viewModel.fetchCharacter(characterId)
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isOled) Color.Black else MaterialTheme.colorScheme.background)
        ) {
            character?.let { char ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp + navigationBarsPadding.calculateBottomPadding())
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp + statusBarsPadding.calculateTopPadding())
                        ) {
                            AsyncImage(
                                model = char.image?.large,
                                contentDescription = char.name?.full,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                if (isOled) Color.Black else MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .padding(top = statusBarsPadding.calculateTopPadding() + 12.dp, start = 16.dp)
                                    .size(40.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .zIndex(10f)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .offset(y = (-40).dp)
                        ) {
                            Text(
                                text = char.name?.full ?: "Unknown",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
                            )
                            char.name?.native?.let { native ->
                                Text(
                                    text = native,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (!char.description.isNullOrEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOled) Color(0xFF0D0D0D).copy(alpha = 0.95f) else Color(0xFF181818).copy(alpha = 0.9f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "About",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val cleanDesc = char.description
                                        .replace("<br>", "\n")
                                        .replace("<br/>", "\n")
                                        .replace("<b>", "")
                                        .replace("</b>", "")
                                        .replace("<i>", "")
                                        .replace("</i>", "")
                                    Text(
                                        cleanDesc,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        }
                    }

                    char.anime?.nodes?.let { animeList ->
                        if (animeList.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(20.dp))
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isOled) Color(0xFF0E0E0E).copy(alpha = 0.95f) else Color(0xFF151515).copy(alpha = 0.9f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Appears In",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            items(animeList) { anime ->
                                                Column(
                                                    modifier = Modifier
                                                        .width(100.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable { onAnimeClick(anime.id) }
                                                ) {
                                                    Card(
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.aspectRatio(3f / 4f)
                                                    ) {
                                                        AsyncImage(
                                                            model = anime.coverImage?.extraLarge,
                                                            contentDescription = anime.title?.romaji,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        anime.title?.english ?: anime.title?.romaji ?: "Unknown",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScreen(
    staffId: Int,
    viewModel: MainViewModel,
    isOled: Boolean = false,
    onDismiss: () -> Unit,
    onAnimeClick: (Int) -> Unit
) {
    val context = LocalContext.current
    var staff by remember { mutableStateOf<StaffData?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues()
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues()

    LaunchedEffect(staffId) {
        isLoading = true
        staff = viewModel.fetchStaff(staffId)
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isOled) Color.Black else MaterialTheme.colorScheme.background)
        ) {
            staff?.let { staffData ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp + navigationBarsPadding.calculateBottomPadding())
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp + statusBarsPadding.calculateTopPadding())
                        ) {
                            AsyncImage(
                                model = staffData.image?.large,
                                contentDescription = staffData.name?.full,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                if (isOled) Color.Black else MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .padding(top = statusBarsPadding.calculateTopPadding() + 12.dp, start = 16.dp)
                                    .size(40.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .zIndex(10f)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .offset(y = (-40).dp)
                        ) {
                            Text(
                                text = staffData.name?.full ?: "Unknown",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
                            )
                            staffData.name?.native?.let { native ->
                                Text(
                                    text = native,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (!staffData.description.isNullOrEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOled) Color(0xFF0D0D0D).copy(alpha = 0.95f) else Color(0xFF181818).copy(alpha = 0.9f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "About",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val cleanDesc = staffData.description
                                        .replace("<br>", "\n")
                                        .replace("<br/>", "\n")
                                        .replace("<b>", "")
                                        .replace("</b>", "")
                                        .replace("<i>", "")
                                        .replace("</i>", "")
                                    Text(
                                        cleanDesc,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isOled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        }
                    }

                    staffData.anime?.nodes?.let { animeList ->
                        if (animeList.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(20.dp))
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isOled) Color(0xFF0E0E0E).copy(alpha = 0.95f) else Color(0xFF151515).copy(alpha = 0.9f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Worked On",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            items(animeList) { anime ->
                                                Column(
                                                    modifier = Modifier
                                                        .width(100.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable { onAnimeClick(anime.id) }
                                                ) {
                                                    Card(
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.aspectRatio(3f / 4f)
                                                    ) {
                                                        AsyncImage(
                                                            model = anime.coverImage?.extraLarge,
                                                            contentDescription = anime.title?.romaji,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        anime.title?.english ?: anime.title?.romaji ?: "Unknown",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}