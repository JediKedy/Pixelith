package com.rahim.pixelith

import android.app.DownloadManager
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.rahim.pixelith.ui.theme.PixelithTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

private const val RANDOM_SHORTCUT_ACTION = "com.rahim.pixelith.ACTION_RANDOM_SILENT"

data class Wallpaper(val name: String, val url: String, val category: String)

interface WallpaperApi {
    @GET("JediKedy/Pixelith/refs/heads/main/wallpapers.json")
    suspend fun getWallpapers(): List<Wallpaper>
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PixelithTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WallpaperScreen(modifier = Modifier, launchIntent = intent)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperScreen(modifier: Modifier = Modifier, launchIntent: Intent? = null) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var wallpaperList by remember { mutableStateOf(listOf<Wallpaper>()) }
    var selectedWallpaper by remember { mutableStateOf<Wallpaper?>(null) }
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        wallpaperList = fetchWallpapers()
        isLoading = false
        if (wallpaperList.isEmpty()) {
            snackbarHostState.showSnackbar("Couldn't load wallpapers. Check your connection and retry.")
        }
    }

    LaunchedEffect(wallpaperList, launchIntent?.action) {
        if (wallpaperList.isNotEmpty() && launchIntent?.action == RANDOM_SHORTCUT_ACTION) {
            val randomWp = wallpaperList.random()
            setWallpaper(context, randomWp.url) { }
            snackbarHostState.showSnackbar("Random wallpaper applied: ${randomWp.name}")
        }
    }

    val categories = remember(wallpaperList) {
        listOf("All") + wallpaperList.map { it.category }.distinct().sorted()
    }

    val filteredList = remember(selectedCategory, searchQuery, wallpaperList) {
        wallpaperList.filter { wp ->
            val matchesCategory = selectedCategory == "All" || wp.category == selectedCategory
            val matchesSearch = wp.name.contains(searchQuery.trim(), ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text("Pixelith", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (selectedWallpaper == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search wallpapers...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp)
                )

                Button(
                    onClick = { createPinnedShortcut(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text("Add silent random shortcut")
                }

                LazyRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        AssistChip(
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (selectedCategory == cat) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(filteredList) { wp ->
                        Card(
                            modifier = Modifier
                                .padding(6.dp)
                                .aspectRatio(0.66f)
                                .clip(RoundedCornerShape(28.dp))
                                .clickable { selectedWallpaper = wp },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                        ) {
                            Box {
                                AsyncImage(
                                    model = wp.url,
                                    contentDescription = wp.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = wp.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = selectedWallpaper!!.url,
                    contentDescription = selectedWallpaper!!.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(onClick = { setWallpaper(context, selectedWallpaper!!.url) {} }) {
                        Text("Set wallpaper")
                    }
                    Button(onClick = { downloadImage(context, selectedWallpaper!!.url, selectedWallpaper!!.name) }) {
                        Text("Download")
                    }
                }

                FilledTonalButton(
                    onClick = { selectedWallpaper = null },
                    modifier = Modifier
                        .padding(top = innerPadding.calculateTopPadding() + 12.dp, start = 16.dp)
                ) {
                    Text("Back")
                }
            }
        }
    }
}

suspend fun fetchWallpapers(): List<Wallpaper> {
    return try {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://raw.githubusercontent.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(WallpaperApi::class.java)
        api.getWallpapers()
    } catch (e: Exception) {
        Log.e("Pixelith", "Error: ${e.message}")
        emptyList()
    }
}

fun createPinnedShortcut(context: Context) {
    val shortcutManager = context.getSystemService(ShortcutManager::class.java)
    if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
        val shortcutIntent = Intent(context, MainActivity::class.java).apply {
            action = RANDOM_SHORTCUT_ACTION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pinShortcutInfo = ShortcutInfo.Builder(context, "random-wallpaper-id")
            .setShortLabel("Randomize")
            .setLongLabel("Silent Random Wallpaper")
            .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(shortcutIntent)
            .build()

        shortcutManager.requestPinShortcut(pinShortcutInfo, null)
        Toast.makeText(context, "Shortcut added to your home screen.", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Pinned shortcuts are not supported on this launcher.", Toast.LENGTH_SHORT).show()
    }
}

fun setWallpaper(context: Context, url: String, onComplete: () -> Unit) {
    val loader = ImageLoader(context)
    val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val result = (loader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (result as? BitmapDrawable)?.bitmap
            CoroutineScope(Dispatchers.Main).launch {
                if (bitmap != null) {
                    WallpaperManager.getInstance(context).setBitmap(bitmap)
                    Toast.makeText(context, "Wallpaper updated!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Couldn't decode image.", Toast.LENGTH_SHORT).show()
                }
                onComplete()
            }
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                onComplete()
            }
        }
    }
}

fun downloadImage(context: Context, url: String, fileName: String) {
    try {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$fileName.jpg")

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
