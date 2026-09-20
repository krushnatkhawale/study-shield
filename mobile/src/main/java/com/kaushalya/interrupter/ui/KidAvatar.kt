package com.kaushalya.interrupter.ui

import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image

/** Initials: first letters of first two words, uppercase. */
fun kidInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "?"
    return parts.take(2).map { it.first().uppercaseChar() }.joinToString("")
}

/** Stable avatar background color derived from the name hash. */
fun kidAvatarColor(name: String): Color {
    val palette = listOf(
        Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFF6B00),
        Color(0xFF8E24AA), Color(0xFF00897B), Color(0xFFC62828)
    )
    val idx = (name.hashCode() and Int.MAX_VALUE) % palette.size
    return palette[idx]
}

@Composable
fun KidAvatar(
    photoUri: String?,
    name: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(photoUri) {
        if (photoUri.isNullOrBlank()) return@remember null
        try {
            val uri = Uri.parse(photoUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val src = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                    decoder.setTargetSize(256, 256)
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (_: Exception) { null }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = name,
            modifier = modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.size(size).clip(CircleShape)
                .background(kidAvatarColor(name)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                kidInitials(name),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
