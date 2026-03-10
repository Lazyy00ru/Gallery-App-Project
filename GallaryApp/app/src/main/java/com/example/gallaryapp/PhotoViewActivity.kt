package com.example.gallaryapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch

class PhotoViewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val photoId = intent.getStringExtra("photo_id") ?: ""
        val orientation = intent.getIntExtra("photo_orientation", 0)
        val width = intent.getIntExtra("photo_width", 0)
        val height = intent.getIntExtra("photo_height", 0)

        val photo = Photo(photoId, orientation, width, height, 0)

        setContent {
            PhotoViewer(photo)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PhotoViewer(photo: Photo) {
        var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        val scope = rememberCoroutineScope()

        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(photo.id) {
            scope.launch {
                bitmap = PhotoLoader.loadFullImage(contentResolver, photo)
                isLoading = false
            }
        }

        MaterialTheme {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Photo") },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Text("←", style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        bitmap != null -> {
                            Image(
                                bitmap = bitmap!!.asImageBitmap(),
                                contentDescription = "Full photo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(1f, 5f)

                                            if (scale > 1f) {
                                                offsetX += pan.x
                                                offsetY += pan.y
                                            } else {
                                                offsetX = 0f
                                                offsetY = 0f
                                            }
                                        }
                                    }
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offsetX
                                        translationY = offsetY
                                    }
                            )
                        }
                        else -> {
                            Text(
                                "Failed to load photo",
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }
}