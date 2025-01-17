package org.matomocamp.companion.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.TypeConverters
import org.matomocamp.companion.api.MatomoCampUrls
import org.matomocamp.companion.db.converters.NullableInstantTypeConverters
import org.matomocamp.companion.utils.InstantParceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import java.time.Duration
import java.time.Instant

@Parcelize
data class Event(
        val id: Long,
        @Embedded(prefix = "day_")
        val day: Day,
        @ColumnInfo(name = "start_time")
        @field:TypeConverters(NullableInstantTypeConverters::class)
        val startTime: @WriteWith<InstantParceler> Instant? = null,
        @ColumnInfo(name = "end_time")
        @field:TypeConverters(NullableInstantTypeConverters::class)
        val endTime: @WriteWith<InstantParceler> Instant? = null,
        @ColumnInfo(name = "room_name")
        val roomName: String?,
        val slug: String?,
        val url: String?,
        val title: String?,
        @ColumnInfo(name = "subtitle")
        val subTitle: String?,
        @Embedded(prefix = "track_")
        val track: Track,
        @ColumnInfo(name = "abstract")
        val abstractText: String?,
        val description: String?,
        @ColumnInfo(name = "persons")
        val personsSummary: String?
) : Parcelable {

    fun isRunningAtTime(time: Instant): Boolean {
        return startTime != null && endTime != null && time >= startTime && time < endTime
    }

    val duration: Duration
        get() = if (startTime == null || endTime == null) {
            Duration.ZERO
        } else {
            Duration.between(startTime, endTime)
        }


    override fun toString(): String = title.orEmpty()
}