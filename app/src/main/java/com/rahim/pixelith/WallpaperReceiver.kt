package com.rahim.pixelith

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class WallpaperReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // This runs in the background when the shortcut is clicked
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://raw.githubusercontent.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                val api = retrofit.create(WallpaperApi::class.java)
                val response = api.getWallpapers()

                if (response.isNotEmpty()) {
                    val randomWp = response.random()
                    // Re-use your existing setWallpaper logic here
                    setWallpaper(context, randomWp.url) {
                        pendingResult.finish()
                    }
                } else {
                    pendingResult.finish()
                }
            } catch (e: Exception) {
                pendingResult.finish()
            }
        }
    }
}