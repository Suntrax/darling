package com.blissless.anime.ui.screens.cast

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.blissless.anime.MainViewModel
import com.blissless.anime.data.models.CharacterData
import com.blissless.anime.data.models.StaffData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllCastScreen(
    animeId: Int,
    animeTitle: String,
    viewModel: MainViewModel,
    isOled: Boolean = false,
    onDismiss: () -> Unit,
    onCharacterClick: (Int) -> Unit,
    onAnimeClick: (Int) -> Unit
) {
    var characters by remember { mutableStateOf<List<CharacterData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(animeId) {
        isLoading = true
        try {
            characters = viewModel.fetchAllCharacters(animeId) ?: emptyList()
        } catch (e: Exception) {
            Log.e("ALL_CAST_DEBUG", "Error fetching characters: ${e.message}")
            characters = emptyList()
        }
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isOled) Color.Black else MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    if (isOled) Color.Black else MaterialTheme.colorScheme.background
                                )
                            )
                        )
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .padding(top = 16.dp, start = 16.dp)
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .zIndex(10f)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Cast",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 16.dp)
                    )
                    Text(
                        text = animeTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp)
                            .widthIn(max = 200.dp)
                    )
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (characters.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No characters found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(characters) { character ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onCharacterClick(character.id) }
                            ) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.75f),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                                ) {
                                    AsyncImage(
                                        model = character.image?.large,
                                        contentDescription = character.name?.full,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = character.name?.full ?: "Unknown",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllStaffScreen(
    animeId: Int,
    animeTitle: String,
    viewModel: MainViewModel,
    isOled: Boolean = false,
    onDismiss: () -> Unit,
    onStaffClick: (Int) -> Unit,
    onAnimeClick: (Int) -> Unit
) {
    var staff by remember { mutableStateOf<List<StaffData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(animeId) {
        isLoading = true
        try {
            staff = viewModel.fetchAllStaff(animeId) ?: emptyList()
        } catch (e: Exception) {
            Log.e("ALL_STAFF_DEBUG", "Error fetching staff: ${e.message}")
            staff = emptyList()
        }
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isOled) Color.Black else MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    if (isOled) Color.Black else MaterialTheme.colorScheme.background
                                )
                            )
                        )
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .padding(top = 16.dp, start = 16.dp)
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .zIndex(10f)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Staff",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 16.dp)
                    )
                    Text(
                        text = animeTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp)
                            .widthIn(max = 200.dp)
                    )
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (staff.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No staff found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(staff) { staffMember ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onStaffClick(staffMember.id) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.75f),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                                ) {
                                    AsyncImage(
                                        model = staffMember.image?.large,
                                        contentDescription = staffMember.name?.full,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = staffMember.name?.full ?: "Unknown",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isOled) Color.White else MaterialTheme.colorScheme.onBackground
                                )
                                staffMember.primaryOccupations?.firstOrNull()?.let { role ->
                                    Text(
                                        text = role,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isOled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}