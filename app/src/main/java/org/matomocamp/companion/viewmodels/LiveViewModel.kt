package org.matomocamp.companion.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import org.matomocamp.companion.db.ScheduleDao
import org.matomocamp.companion.flow.countSubscriptionsFlow
import org.matomocamp.companion.flow.flowWhileShared
import org.matomocamp.companion.flow.stateFlow
import org.matomocamp.companion.flow.synchronizedTickerFlow
import org.matomocamp.companion.model.StatusEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@HiltViewModel
class LiveViewModel @Inject constructor(scheduleDao: ScheduleDao) : ViewModel() {

    // Share a single ticker providing the time to ensure both lists are synchronized
    private val ticker: Flow<Instant> = stateFlow(viewModelScope, null) { subscriptionCount ->
        synchronizedTickerFlow(REFRESH_PERIOD, subscriptionCount)
            .map { Instant.now() }
    }.filterNotNull()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createLiveEventsHotFlow(
        pagingSourceFactory: (now: Instant) -> PagingSource<Int, StatusEvent>
    ): Flow<PagingData<StatusEvent>> {
        return countSubscriptionsFlow { subscriptionCount ->
            ticker
                .flowWhileShared(subscriptionCount, SharingStarted.WhileSubscribed())
                .distinctUntilChanged()
                .flatMapLatest { now ->
                    Pager(PagingConfig(20)) { pagingSourceFactory(now) }.flow
                }.cachedIn(viewModelScope)
        }
    }

    val nextEvents: Flow<PagingData<StatusEvent>> = createLiveEventsHotFlow { now ->
        scheduleDao.getEventsWithStartTime(now, now + NEXT_EVENTS_INTERVAL)
    }

    val eventsInProgress: Flow<PagingData<StatusEvent>> = createLiveEventsHotFlow { now ->
        scheduleDao.getEventsInProgress(now)
    }

    val allEvents: Flow<PagingData<StatusEvent>> = createLiveEventsHotFlow { now ->
        scheduleDao.getEvents()
    }



    companion object {
        private val REFRESH_PERIOD = 1.minutes
        private val NEXT_EVENTS_INTERVAL = Duration.ofHours(3L)
    }
}