package org.matomocamp.companion.fragments

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.text.set
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.core.view.plusAssign
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import org.matomocamp.companion.R
import org.matomocamp.companion.activities.PersonInfoActivity
import org.matomocamp.companion.api.MatomoCampApi
import org.matomocamp.companion.model.Building
import org.matomocamp.companion.model.Event
import org.matomocamp.companion.model.EventDetails
import org.matomocamp.companion.model.Link
import org.matomocamp.companion.model.Person
import org.matomocamp.companion.model.RoomStatus
import org.matomocamp.companion.settings.UserSettingsProvider
import org.matomocamp.companion.utils.ClickableArrowKeyMovementMethod
import org.matomocamp.companion.utils.DateUtils
import org.matomocamp.companion.utils.assistedViewModels
import org.matomocamp.companion.utils.configureToolbarColors
import org.matomocamp.companion.utils.launchAndRepeatOnLifecycle
import org.matomocamp.companion.utils.parseHtml
import org.matomocamp.companion.utils.roomNameToResourceName
import org.matomocamp.companion.utils.stripHtml
import org.matomocamp.companion.viewmodels.EventDetailsViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class EventDetailsFragment : Fragment(R.layout.fragment_event_details) {

    private class ViewHolder(view: View) {
        val personsTextView: TextView = view.findViewById(R.id.persons)
        val timeTextView: TextView = view.findViewById(R.id.time)
        val roomStatusTextView: TextView = view.findViewById(R.id.room_status)
        val linksHeader: View = view.findViewById(R.id.links_header)
        val linksContainer: ViewGroup = view.findViewById(R.id.links_container)
    }

    @Inject
    lateinit var userSettingsProvider: UserSettingsProvider
    @Inject
    lateinit var api: MatomoCampApi
    @Inject
    lateinit var viewModelFactory: EventDetailsViewModel.Factory
    private val viewModel: EventDetailsViewModel by assistedViewModels {
        viewModelFactory.create(event)
    }

    val event by lazy<Event>(LazyThreadSafetyMode.NONE) {
        requireArguments().getParcelable(ARG_EVENT)!!
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().addMenuProvider(EventDetailsMenuProvider(), this, Lifecycle.State.RESUMED)
    }

    private inner class EventDetailsMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.event, menu)
            menu.findItem(R.id.share)?.intent = createShareChooserIntent()
        }

        private fun createShareChooserIntent(): Intent {
            val title = event.title.orEmpty()
            val url = event.url.orEmpty()
            return ShareCompat.IntentBuilder(requireContext())
                .setSubject("$title ($CONFERENCE_NAME)")
                .setType("text/plain")
                .setText("$title $url $CONFERENCE_HASHTAG")
                .setChooserTitle(R.string.share)
                .createChooserIntent()
        }

        override fun onMenuItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
            R.id.add_to_agenda -> {
                addToAgenda()
                true
            }
            R.id.open_in_webbrowser -> {
                openInWebbrowser()
                true
            }
            else -> false
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val holder = ViewHolder(view).apply {
            view.findViewById<TextView>(R.id.title).text = event.title
            view.findViewById<TextView>(R.id.subtitle).apply {
                val subTitle = event.subTitle
                if (subTitle.isNullOrEmpty()) {
                    isVisible = false
                } else {
                    text = subTitle
                }
            }

            personsTextView.apply {
                // Set the persons summary text first;
                // replace it with the clickable text when the event details loading completes
                val personsSummary = event.personsSummary
                if (personsSummary.isNullOrEmpty()) {
                    isVisible = false
                } else {
                    text = personsSummary
                    movementMethod = LinkMovementMethod.getInstance()
                    isVisible = true
                }
            }

            view.findViewById<TextView>(R.id.time).apply {
                val timeFormatter = DateUtils.getTimeFormatter(context)
                val startTime = event.startTime?.atZone(DateUtils.conferenceZoneId)?.format(timeFormatter) ?: "?"
                val endTime = event.endTime?.atZone(DateUtils.conferenceZoneId)?.format(timeFormatter) ?: "?"
                text = "${event.day}, $startTime ― $endTime"
                contentDescription = getString(R.string.time_content_description, text)
            }

            view.findViewById<TextView>(R.id.room).apply {
                val roomName = event.roomName
                if (roomName.isNullOrEmpty()) {
                    isVisible = false
                } else {
                    val building = Building.fromRoomName(roomName)
                    val roomText = SpannableString(if (building == null) roomName else getString(R.string.room_building, roomName, building))
                    val roomImageResId = resources.getIdentifier(roomNameToResourceName(roomName), "drawable", requireActivity().packageName)
                    // If the room image exists, make the room text clickable to display it
                    if (roomImageResId != 0) {
                        roomText[0, roomText.length] = object : ClickableSpan() {
                            override fun onClick(view: View) {
                                parentFragmentManager.commit(allowStateLoss = true) {
                                    add<RoomImageDialogFragment>(RoomImageDialogFragment.TAG,
                                            args = RoomImageDialogFragment.createArguments(roomName, roomImageResId))
                                }
                            }

                            override fun updateDrawState(ds: TextPaint) {
                                super.updateDrawState(ds)
                                ds.isUnderlineText = false
                            }
                        }
                        movementMethod = LinkMovementMethod.getInstance()
                    }
                    text = roomText
                    contentDescription = getString(R.string.room_content_description, roomText)
                }
            }

            view.findViewById<TextView>(R.id.abstract_text).apply {
                val abstractText = event.abstractText
                if (abstractText.isNullOrEmpty()) {
                    isVisible = false
                } else {
                    text = abstractText.parseHtml(resources)
                    movementMethod = ClickableArrowKeyMovementMethod
                }
            }

            view.findViewById<TextView>(R.id.description).apply {
                val descriptionText = event.description
                if (descriptionText.isNullOrEmpty()) {
                    isVisible = false
                } else {
                    text = descriptionText.parseHtml(resources)
                    movementMethod = ClickableArrowKeyMovementMethod
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            showEventDetails(holder, viewModel.eventDetails.await())
        }

        val timeFormatter = DateUtils.getTimeFormatter(view.context)
        val roomName = event.roomName
        viewLifecycleOwner.launchAndRepeatOnLifecycle {
            launch {
                userSettingsProvider.zoneId.collect { zoneId ->
                    bindTime(holder.timeTextView, timeFormatter, zoneId)
                }
            }

            // Live room status
            if (!roomName.isNullOrEmpty()) {
                launch {
                    api.roomStatuses.collect { statuses ->
                        bindRoomStatus(holder.roomStatusTextView, statuses[roomName])
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun bindTime(timeTextView: TextView, timeFormatter: DateTimeFormatter, zoneId: ZoneId) {
        val startTime = event.startTime?.atZone(zoneId)?.format(timeFormatter) ?: "?"
        val endTime = event.endTime?.atZone(zoneId)?.format(timeFormatter) ?: "?"
        timeTextView.text = "${event.day}, $startTime ― $endTime"
        timeTextView.contentDescription = getString(R.string.time_content_description, timeTextView.text)
    }

    private fun bindRoomStatus(roomStatusTextView: TextView, roomStatus: RoomStatus?) {
        if (roomStatus == null) {
            roomStatusTextView.text = null
        } else {
            roomStatusTextView.setText(roomStatus.nameResId)
            roomStatusTextView.setTextColor(
                ContextCompat.getColorStateList(roomStatusTextView.context, roomStatus.colorResId)
            )
        }
    }
    private fun openInWebbrowser() {
        try {
            val context = activity
            if (context != null) {
                CustomTabsIntent.Builder()
                    .configureToolbarColors(context, R.color.light_color_primary)
                    .build()
                    .launchUrl(context, Uri.parse(event.url))
            }
        } catch (ignore: ActivityNotFoundException) {
        }

    }
    private fun addToAgenda() {
        val intent = Intent(Intent.ACTION_EDIT).apply {
            type = "vnd.android.cursor.item/event"
            event.title?.let { putExtra(CalendarContract.Events.TITLE, it) }
            val roomName = event.roomName
            val location = if (roomName.isNullOrEmpty()) VENUE_NAME else "$VENUE_NAME - $roomName"
            putExtra(CalendarContract.Events.EVENT_LOCATION, location)

            var description = event.abstractText
            if (description.isNullOrEmpty()) {
                description = event.description.orEmpty()
            }
            description = description.stripHtml()
            // Add speaker info if available
            val personsSummary = event.personsSummary
            if (!personsSummary.isNullOrBlank()) {
                val personsCount = personsSummary.count { it == ',' } + 1
                val speakersLabel = resources.getQuantityString(R.plurals.speakers, personsCount)
                description = "$speakersLabel: $personsSummary\n\n$description"
            }
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            event.startTime?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it.toEpochMilli()) }
            event.endTime?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it.toEpochMilli()) }
        }

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(requireView(), R.string.calendar_not_found, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showEventDetails(holder: ViewHolder, eventDetails: EventDetails) {
        holder.run {
            val (persons, links) = eventDetails

            // 1. Persons
            if (persons.isNotEmpty()) {
                // Build a list of clickable persons
                val clickablePersonsSummary = buildSpannedString {
                    for (person in persons) {
                        val name = person.name
                        if (name.isNullOrEmpty()) {
                            continue
                        }
                        if (length != 0) {
                            append(", ")
                        }
                        inSpans(PersonClickableSpan(person)) {
                            append(name)
                        }
                    }
                }
                personsTextView.text = clickablePersonsSummary
                personsTextView.isVisible = true
            }

            // 2. Links
            linksContainer.removeAllViews()
            if (links.isNotEmpty()) {
                linksHeader.isVisible = true
                linksContainer.isVisible = true
                val inflater = layoutInflater
                for (link in links) {
                    val view = inflater.inflate(R.layout.item_link, linksContainer, false)
                    view.findViewById<TextView>(R.id.description).apply {
                        text = link.description
                    }
                    view.setOnClickListener(LinkClickListener(event, link))
                    linksContainer += view
                }
            } else {
                linksHeader.isVisible = false
                linksContainer.isVisible = false
            }
        }
    }

    private class PersonClickableSpan(private val person: Person) : ClickableSpan() {
        override fun onClick(v: View) {
            val context = v.context
            val intent = Intent(context, PersonInfoActivity::class.java)
                    .putExtra(PersonInfoActivity.EXTRA_PERSON, person)
            context.startActivity(intent)
        }

        override fun updateDrawState(ds: TextPaint) {
            super.updateDrawState(ds)
            ds.isUnderlineText = false
        }
    }

    private class LinkClickListener(private val event: Event, private val link: Link) : View.OnClickListener {
        override fun onClick(v: View) {
            try {
                val context = v.context
                CustomTabsIntent.Builder()
                        .configureToolbarColors(context, event.track.appBarColorResId)
                        .setShowTitle(true)
                        .setStartAnimations(context, R.anim.slide_in_right, R.anim.slide_out_left)
                        .setExitAnimations(context, R.anim.slide_in_left, R.anim.slide_out_right)
                        .build()
                        .launchUrl(context, link.url.toUri())
            } catch (ignore: ActivityNotFoundException) {
            }
        }
    }

    companion object {
        private const val ARG_EVENT = "event"
        private const val CONFERENCE_NAME = "MatomoCamp"
        private const val CONFERENCE_HASHTAG = "#MatomoCamp"
        private const val VENUE_NAME = "ULB"

        fun createArguments(event: Event) = Bundle(1).apply {
            putParcelable(ARG_EVENT, event)
        }
    }
}