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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

// --- 1. DATA MODELS ---
data class Wallpaper(val name: String, val url: String, val category: String)

interface WallpaperApi {
    @GET("JediKedy/Pixelith/refs/heads/main/wallpapers.json")
    suspend fun getWallpapers(): List<Wallpaper>
}

// --- 2. MAIN ACTIVITY ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PixelithTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WallpaperScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

// --- 3. UI COMPONENTS ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // States
    var wallpaperList by remember { mutableStateOf(listOf<Wallpaper>()) }
    var selectedWallpaper by remember { mutableStateOf<Wallpaper?>(null) }
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // Data Fetching
    LaunchedEffect(Unit) {
        try {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://raw.githubusercontent.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val api = retrofit.create(WallpaperApi::class.java)
            wallpaperList = api.getWallpapers()
            isLoading = false
        } catch (e: Exception) {
            isLoading = false
            Log.e("Pixelith", "Error: ${e.message}")
        }
    }

    val categories = remember(wallpaperList) {
        listOf("All") + wallpaperList.map { it.category }.distinct().sorted()
    }

    val filteredList = remember(selectedCategory, searchQuery, wallpaperList) {
        wallpaperList.filter { wp ->
            val matchesCategory = (selectedCategory == "All" || wp.category == selectedCategory)
            val matchesSearch = wp.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    if (isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (selectedWallpaper == null) {
        Column(modifier = modifier.fillMaxSize()) {

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search wallpapers...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            // Silent Shortcut Button
            Button(
                onClick = { createPinnedShortcut(context) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Add Silent Random Shortcut")
            }

            // Categories
            LazyRow(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            // Grid
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize()) {
                items(filteredList) { wp ->
                    Card(
                        modifier = Modifier
                            .padding(4.dp)
                            .aspectRatio(0.6f)
                            .clickable { selectedWallpaper = wp }
                    ) {
                        AsyncImage(
                            model = wp.url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    } else {
        // Preview Mode
        Box(modifier = modifier.fillMaxSize()) {
            AsyncImage(
                model = selectedWallpaper!!.url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = { setWallpaper(context, selectedWallpaper!!.url) {} }) {
                    Text("Set")
                }
                Button(onClick = { downloadImage(context, selectedWallpaper!!.url, selectedWallpaper!!.name) }) {
                    Text("Download")
                }
            }

            FilledTonalButton(
                onClick = { selectedWallpaper = null },
                modifier = Modifier.padding(top = 48.dp, start = 16.dp)
            ) {
                Text("Back")
            }
        }
    }
}

// --- 4. SYSTEM LOGIC ---

fun createPinnedShortcut(context: Context) {
    val shortcutManager = context.getSystemService(ShortcutManager::class.java)
    if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {

        // This Intent now points to WallpaperReceiver instead of MainActivity
        val intent = Intent(context, WallpaperReceiver::class.java).apply {
            action = "com.rahim.pixelith.ACTION_RANDOM_SILENT"
        }

        val pinShortcutInfo = ShortcutInfo.Builder(context, "random-wallpaper-id")
            .setShortLabel("Randomize")
            .setLongLabel("Silent Random Wallpaper")
            .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()

        shortcutManager.requestPinShortcut(pinShortcutInfo, null)
        Toast.makeText(context, "Shortcut added! Try it from home screen.", Toast.LENGTH_SHORT).show()
    }
}

fun setWallpaper(context: Context, url: String, onComplete: () -> Unit) {
    val loader = ImageLoader(context)
    val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val result = (loader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (result as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                WallpaperManager.getInstance(context).setBitmap(bitmap)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Wallpaper Updated!", Toast.LENGTH_SHORT).show()
                    onComplete()
                }
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
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
    }
}