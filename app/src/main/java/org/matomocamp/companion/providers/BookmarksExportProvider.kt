package org.matomocamp.companion.providers

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.app.ShareCompat
import androidx.core.content.ContentProviderCompat
import org.matomocamp.companion.BuildConfig
import org.matomocamp.companion.R
import org.matomocamp.companion.api.MatomoCampUrls
import org.matomocamp.companion.db.BookmarksDao
import org.matomocamp.companion.db.ScheduleDao
import org.matomocamp.companion.ical.ICalendarWriter
import org.matomocamp.companion.model.Event
import org.matomocamp.companion.utils.stripHtml
import org.matomocamp.companion.utils.toSlug
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okio.buffer
import okio.sink
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Content Provider generating the current bookmarks list in iCalendar format.
 */
class BookmarksExportProvider : ContentProvider() {

    private val scheduleDao: ScheduleDao by lazy {
        EntryPointAccessors.fromApplication(
            ContentProviderCompat.requireContext(this),
            BookmarksExportProviderEntryPoint::class.java
        ).scheduleDao
    }
    private val bookmarksDao: BookmarksDao by lazy {
        EntryPointAccessors.fromApplication(
            ContentProviderCompat.requireContext(this),
            BookmarksExportProviderEntryPoint::class.java
        ).bookmarksDao
    }

    override fun onCreate() = true

    override fun insert(uri: Uri, values: ContentValues?) = throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = throw UnsupportedOperationException()

    override fun getType(uri: Uri) = TYPE

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val ctx = ContentProviderCompat.requireContext(this)
        val proj = projection ?: COLUMNS
        val cols = arrayOfNulls<String>(proj.size)
        val values = arrayOfNulls<Any>(proj.size)
        var columnCount = 0
        for (col in proj) {
            when (col) {
                OpenableColumns.DISPLAY_NAME -> {
                    cols[columnCount] = OpenableColumns.DISPLAY_NAME
                    val year = runBlocking { scheduleDao.getYear() }
                    values[columnCount++] = ctx.getString(R.string.export_bookmarks_file_name, year)
                }
                OpenableColumns.SIZE -> {
                    cols[columnCount] = OpenableColumns.SIZE
                    // Unknown size, content will be generated on-the-fly
                    values[columnCount++] = 1024L
                }
            }
        }

        val cursor = MatrixCursor(cols.copyOfRange(0, columnCount), 1)
        cursor.addRow(values.copyOfRange(0, columnCount))
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        return try {
            val pipe = ParcelFileDescriptor.createPipe()
            DownloadThread(ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]), bookmarksDao).start()
            pipe[0]
        } catch (e: IOException) {
            throw FileNotFoundException("Could not open pipe")
        }
    }

    private class DownloadThread(private val outputStream: OutputStream, private val bookmarksDao: BookmarksDao) : Thread() {
        private val dtStamp = LocalDateTime.now(ZoneOffset.UTC).format(DATE_TIME_FORMAT)

        override fun run() {
            try {
                ICalendarWriter(outputStream.sink().buffer()).use { writer ->
                    val bookmarks = runBlocking { bookmarksDao.getBookmarks() }
                    writer.write("BEGIN", "VCALENDAR")
                    writer.write("VERSION", "2.0")
                    writer.write("PRODID", "-//${BuildConfig.APPLICATION_ID}//NONSGML ${BuildConfig.VERSION_NAME}//EN")

                    for (event in bookmarks) {
                        writeEvent(writer, event)
                    }

                    writer.write("END", "VCALENDAR")
                }
            } catch (ignore: Exception) {
            }
        }

        @Throws(IOException::class)
        private fun writeEvent(writer: ICalendarWriter, event: Event) = with(writer) {
            write("BEGIN", "VEVENT")

            val year = event.day.date.year
            write("UID", "${event.id}@$year@${BuildConfig.APPLICATION_ID}")
            write("DTSTAMP", dtStamp)
            event.startTime?.let { write("DTSTART", it.atOffset(ZoneOffset.UTC).format(DATE_TIME_FORMAT)) }
            event.endTime?.let { write("DTEND", it.atOffset(ZoneOffset.UTC).format(DATE_TIME_FORMAT)) }
            write("SUMMARY", event.title)
            var description = event.abstractText
            if (description.isNullOrEmpty()) {
                description = event.description
            }
            if (!description.isNullOrEmpty()) {
                write("DESCRIPTION", description.stripHtml())
                write("X-ALT-DESC", description)
            }
            write("CLASS", "PUBLIC")
            write("CATEGORIES", event.track.name)
            write("URL", event.url)
            write("LOCATION", event.roomName)


            write("END", "VEVENT")
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BookmarksExportProviderEntryPoint {
        val scheduleDao: ScheduleDao
        val bookmarksDao: BookmarksDao
    }

    companion object {
        const val TYPE = "text/calendar"
        private val URI = Uri.Builder()
                .scheme("content")
                .authority("${BuildConfig.APPLICATION_ID}.bookmarks")
                .appendPath("bookmarks.ics")
                .build()
        private val COLUMNS = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US)

        fun getIntent(activity: Activity): Intent {
            // Supports granting read permission for the attached shared file
            return ShareCompat.IntentBuilder(activity)
                    .setStream(URI)
                    .setType(TYPE)
                    .intent
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}