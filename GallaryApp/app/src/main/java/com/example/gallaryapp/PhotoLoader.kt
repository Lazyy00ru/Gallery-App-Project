package com.example.gallaryapp

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

object PhotoLoader {

    suspend fun loadPhotos(contentResolver: ContentResolver): List<Photo> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Photo>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.ORIENTATION,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val orientationColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getString(idColumn)
                val orientation = cursor.getInt(orientationColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val dateAdded = cursor.getLong(dateColumn)

                photos.add(Photo(id, orientation, width, height, dateAdded))
            }
        }

        photos
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight &&
                halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    suspend fun loadThumbnail(
        contentResolver: ContentResolver,
        photo: Photo,
        targetSize: Int = 300
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "thumb_${photo.id}"

        ThumbnailCache.getBitmap(cacheKey)?.let { return@withContext it }

        try {
            val uri = Uri.withAppendedPath(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                photo.id
            )

            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(stream, null, options)

                options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
                options.inJustDecodeBounds = false

                val bitmap = contentResolver.openInputStream(uri)?.use { newStream ->
                    BitmapFactory.decodeStream(newStream, null, options)
                }

                val rotatedBitmap = bitmap?.let { rotateBitmap(it, photo.orientation) }

                rotatedBitmap?.let {
                    ThumbnailCache.addBitmap(cacheKey, it)
                }

                rotatedBitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun loadFullImage(
        contentResolver: ContentResolver,
        photo: Photo,
        maxSize: Int = 2048
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.withAppendedPath(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                photo.id
            )

            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(stream, null, options)

                options.inSampleSize = calculateInSampleSize(options, maxSize, maxSize)
                options.inJustDecodeBounds = false

                val bitmap = contentResolver.openInputStream(uri)?.use { newStream ->
                    BitmapFactory.decodeStream(newStream, null, options)
                }

                bitmap?.let { rotateBitmap(it, photo.orientation) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        if (orientation == 0) return bitmap

        val matrix = Matrix()
        matrix.postRotate(orientation.toFloat())

        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
    }
}