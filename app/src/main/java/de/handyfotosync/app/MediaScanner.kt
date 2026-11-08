package de.handyfotosync.app

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.BaseColumns
import android.provider.MediaStore

data class MediaItem(
    val uri: Uri,
    val key: String,
    val name: String,
    val size: Long,
    val dateMs: Long,
    val isVideo: Boolean
)

/** Liest Fotos (und optional Videos) aus der Mediathek des Handys. */
object MediaScanner {

    fun scan(ctx: Context, includeVideos: Boolean, onlyCamera: Boolean): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        query(ctx, MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), false, onlyCamera, out)
        if (includeVideos) {
            query(ctx, MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), true, onlyCamera, out)
        }
        out.sortBy { it.dateMs }
        return out
    }

    private fun query(
        ctx: Context,
        collection: Uri,
        isVideo: Boolean,
        onlyCamera: Boolean,
        out: MutableList<MediaItem>
    ) {
        val projection = arrayOf(
            BaseColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED
        )
        var selection = "${MediaStore.MediaColumns.IS_PENDING} = 0"
        var args: Array<String>? = null
        if (onlyCamera) {
            selection += " AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            args = arrayOf("DCIM/%")
        }

        ctx.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(BaseColumns._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val size = c.getLong(iSize)
                if (size <= 0) continue
                val name = c.getString(iName) ?: "datei_$id"
                val taken = if (c.isNull(iTaken)) 0L else c.getLong(iTaken)
                val date = if (taken > 0) taken else c.getLong(iAdded) * 1000L
                val prefix = if (isVideo) "v" else "i"
                out.add(
                    MediaItem(
                        uri = ContentUris.withAppendedId(collection, id),
                        key = "$prefix:$id:$size",
                        name = name,
                        size = size,
                        dateMs = date,
                        isVideo = isVideo
                    )
                )
            }
        }
    }
}
