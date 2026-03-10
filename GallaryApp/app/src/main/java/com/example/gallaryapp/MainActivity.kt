package com.example.gallaryapp

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    // Permission launcher to request READ_MEDIA_IMAGES or READ_EXTERNAL_STORAGE permission
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted - show the gallery
            setContent {
                GalleryApp()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Check and request permissions on first launch
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        // Reload gallery when returning to the app (to detect newly added/deleted photos)
        if (hasPermission()) {
            setContent {
                GalleryApp()
            }
        }
    }

    /**
     * Check if we have permission to read photos, request if not
     */
    private fun checkPermissions() {
        // Use READ_MEDIA_IMAGES for Android 13+ (API 33+), READ_EXTERNAL_STORAGE for older versions
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            hasPermission() -> {
                // Already have permission - show gallery
                setContent {
                    GalleryApp()
                }
            }
            else -> {
                // Request permission
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    /**
     * Check currently have permission to read photos
     */
    private fun hasPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(
            this, permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Main Gallery Composable
     * Shows a grid of photos with dynamic column count based on zoom level
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun GalleryApp() {
        // Detect orientation changes - THIS IS THE KEY FIX
        val configuration = LocalConfiguration.current
        val currentOrientation = configuration.orientation

        // State variables
        var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var refreshTrigger by remember { mutableIntStateOf(0) } // Increment to reload photos
        val scope = rememberCoroutineScope()

        // Zoom level: lower = more columns (zoomed out), higher = fewer columns (zoomed in)
        var zoomLevel by remember { mutableFloatStateOf(1f) }

        // Calculate number of columns based on zoom level
        val columns = when {
            zoomLevel < 0.45f -> 8
            zoomLevel < 0.55f -> 7
            zoomLevel < 0.68f -> 6
            zoomLevel < 0.82f -> 5
            zoomLevel < 0.98f -> 4
            zoomLevel < 1.15f -> 3
            zoomLevel < 1.35f -> 2
            else -> 1
        }

        // Define zoom limits to prevent over-zooming
        val minZoom = 0.3f  // Maximum zoom out
        val maxZoom = 1.35f // Maximum zoom in

        // State for delete functionality
        var pendingDeletePhoto by remember { mutableStateOf<Photo?>(null) }

        // Launcher for requesting delete permission (Android 10+)
        val deletePermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                // User granted permission to delete
                pendingDeletePhoto?.let { photo ->
                    scope.launch {
                        performDelete(photo)
                        refreshTrigger++ // Reload gallery to remove deleted photo
                        pendingDeletePhoto = null
                    }
                }
            } else {
                pendingDeletePhoto = null
            }
        }

        // Load photos from MediaStore when app starts or after deletion
        LaunchedEffect(refreshTrigger) {
            isLoading = true
            photos = PhotoLoader.loadPhotos(contentResolver)
            isLoading = false
        }

        MaterialTheme {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Gallery") }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Outer Box with zoom gesture detection
                    // KEY FIX: Use currentOrientation instead of Unit
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(currentOrientation) { // <-- CRITICAL FIX HERE
                                // Detect pinch-to-zoom gestures
                                detectTransformGestures { _, _, zoom, _ ->
                                    // Only apply zoom if it's significant (not accidental touch)
                                    if (zoom != 1f && kotlin.math.abs(zoom - 1f) > 0.01f) {
                                        val newZoom = (zoomLevel * zoom).coerceIn(minZoom, maxZoom)
                                        android.util.Log.d("GalleryZoom", "Orientation: $currentOrientation, Zoom: $zoom, NewZoom: $newZoom")
                                        zoomLevel = newZoom
                                    }
                                }
                            }
                    ) {
                        when {
                            // Show loading indicator while photos are being loaded
                            isLoading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            // Show message if no photos found
                            photos.isEmpty() -> {
                                Text(
                                    "No photos found",
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            // Show photo grid
                            else -> {
                                // LazyVerticalGrid for efficient scrolling with many photos
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(columns),
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    items(photos, key = { it.id }) { photo ->
                                        PhotoGridItem(
                                            photo = photo,
                                            onDeleteRequest = { photoToDelete ->
                                                pendingDeletePhoto = photoToDelete
                                                scope.launch {
                                                    requestDeletePermission(
                                                        photoToDelete,
                                                        deletePermissionLauncher
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }

                                // Zoom buttons for manual control (backup for gesture controls)
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Zoom out button (- button)
                                    FloatingActionButton(
                                        onClick = {
                                            zoomLevel = (zoomLevel - 0.2f).coerceIn(minZoom, maxZoom)
                                        }
                                    ) {
                                        Text("-", style = MaterialTheme.typography.headlineMedium)
                                    }
                                    // Zoom in button (+ button)
                                    FloatingActionButton(
                                        onClick = {
                                            zoomLevel = (zoomLevel + 0.2f).coerceIn(minZoom, maxZoom)
                                        }
                                    ) {
                                        Text("+", style = MaterialTheme.typography.headlineMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Individual photo grid item composable
     * Shows a thumbnail with click and long-press functionality
     */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun PhotoGridItem(photo: Photo, onDeleteRequest: (Photo) -> Unit) {
        var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        val scope = rememberCoroutineScope()
        var isVisible by remember { mutableStateOf(false) }
        var showDeleteDialog by remember { mutableStateOf(false) }

        // Load thumbnail in background using coroutine
        LaunchedEffect(photo.id) {
            bitmap = PhotoLoader.loadThumbnail(contentResolver, photo)
            isVisible = true
        }

        Box(
            modifier = Modifier
                .aspectRatio(1f) // Square thumbnails
                .padding(2.dp)
                .combinedClickable(
                    // Single click: open photo in full screen
                    onClick = {
                        val intent = Intent(this@MainActivity, PhotoViewActivity::class.java).apply {
                            putExtra("photo_id", photo.id)
                            putExtra("photo_orientation", photo.orientation)
                            putExtra("photo_width", photo.width)
                            putExtra("photo_height", photo.height)
                        }
                        startActivity(intent)
                    },
                    // Long press: show delete dialog
                    onLongClick = {
                        showDeleteDialog = true
                    }
                )
        ) {
            bitmap?.let {
                // Animate thumbnail appearance with fade and scale
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(animationSpec = tween(300)) +
                            scaleIn(initialScale = 0.8f, animationSpec = tween(300))
                ) {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Photo thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } ?: CircularProgressIndicator(
                // Show loading spinner while thumbnail loads
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Center)
            )
        }

        // Delete confirmation dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Photo") },
                text = { Text("Are you sure you want to delete this photo?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            onDeleteRequest(photo)
                        }
                    ) {
                        Text("Delete", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    /**
     * Request permission to delete a photo
     */
    private suspend fun requestDeletePermission(
        photo: Photo,
        launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>
    ) = withContext(Dispatchers.IO) {
        try {
            val uri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                photo.id.toLong()
            )
            // Try to delete directly
            contentResolver.delete(uri, null, null)
            withContext(Dispatchers.Main) {
            }
        } catch (securityException: SecurityException) {
            // Android 10+ requires user permission to delete
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val recoverableSecurityException = securityException as? RecoverableSecurityException
                    ?: throw securityException

                withContext(Dispatchers.Main) {
                    // Launch system dialog to request delete permission
                    val intentSender = recoverableSecurityException.userAction.actionIntent.intentSender
                    launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
            } else {
                throw securityException
            }
        }
    }

    /**
     * Actually delete the photo from MediaStore
     * Called after permission is granted
     */
    private suspend fun performDelete(photo: Photo) = withContext(Dispatchers.IO) {
        try {
            val uri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                photo.id.toLong()
            )
            // Delete from MediaStore
            contentResolver.delete(uri, null, null)
            // Remove from cache to free memory
            ThumbnailCache.removeBitmap("thumb_${photo.id}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}