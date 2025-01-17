package org.matomocamp.companion.fragments

import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.nfc.NdefRecord
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.edit
import androidx.core.view.MenuProvider
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import org.matomocamp.companion.R
import org.matomocamp.companion.activities.ExternalBookmarksActivity
import org.matomocamp.companion.adapters.BookmarksAdapter
import org.matomocamp.companion.api.MatomoCampApi
import org.matomocamp.companion.providers.BookmarksExportProvider
import org.matomocamp.companion.settings.UserSettingsProvider
import org.matomocamp.companion.utils.CreateNfcAppDataCallback
import org.matomocamp.companion.utils.launchAndRepeatOnLifecycle
import org.matomocamp.companion.utils.toBookmarksNfcAppData
import org.matomocamp.companion.viewmodels.BookmarksViewModel
import org.matomocamp.companion.widgets.MultiChoiceHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Named

/**
 * Bookmarks list, optionally filterable.
 *
 * @author Christophe Beyls
 */
@AndroidEntryPoint
class BookmarksListFragment : Fragment(R.layout.recyclerview), CreateNfcAppDataCallback {

    @Inject
    lateinit var userSettingsProvider: UserSettingsProvider
    @Inject
    @Named("UIState")
    lateinit var preferences: SharedPreferences
    @Inject
    lateinit var api: MatomoCampApi

    private val viewModel: BookmarksViewModel by viewModels()
    private val multiChoiceHelper: MultiChoiceHelper by lazy(LazyThreadSafetyMode.NONE) {
        MultiChoiceHelper(requireActivity() as AppCompatActivity, this, object : MultiChoiceHelper.MultiChoiceModeListener {

            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                mode.menuInflater.inflate(R.menu.action_mode_bookmarks, menu)
                return true
            }

            private fun updateSelectedCountDisplay(mode: ActionMode) {
                val count = multiChoiceHelper.checkedItemCount
                mode.title = resources.getQuantityString(R.plurals.selected, count, count)
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                updateSelectedCountDisplay(mode)
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = when (item.itemId) {
                R.id.delete -> {
                    // Remove multiple bookmarks at once
                    viewModel.removeBookmarks(multiChoiceHelper.checkedItemIds)
                    mode.finish()
                    true
                }
                else -> false
            }

            override fun onItemCheckedStateChanged(mode: ActionMode, position: Int, id: Long, checked: Boolean) {
                updateSelectedCountDisplay(mode)
            }

            override fun onDestroyActionMode(mode: ActionMode) {}
        })
    }
    private val getBookmarksLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            importBookmarks(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.upcomingOnly = preferences.getBoolean(UPCOMING_ONLY_PREF_KEY, false)

        requireActivity().addMenuProvider(BookmarksMenuProvider(), this)
    }

    private inner class BookmarksMenuProvider : MenuProvider {
        private var filterMenuItem: MenuItem? = null
        private var upcomingOnlyMenuItem: MenuItem? = null

        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.bookmarks, menu)
            filterMenuItem = menu.findItem(R.id.filter)
            upcomingOnlyMenuItem = menu.findItem(R.id.upcoming_only)
            updateMenuItems()
        }

        override fun onMenuItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
            R.id.upcoming_only -> {
                val upcomingOnly = !viewModel.upcomingOnly
                viewModel.upcomingOnly = upcomingOnly
                updateMenuItems()
                preferences.edit {
                    putBoolean(UPCOMING_ONLY_PREF_KEY, upcomingOnly)
                }
                true
            }
            R.id.export_bookmarks -> {
                val exportIntent = BookmarksExportProvider.getIntent(requireActivity())
                startActivity(Intent.createChooser(exportIntent, getString(R.string.export_bookmarks)))
                true
            }
            R.id.import_bookmarks -> {
                getBookmarksLauncher.launch(BookmarksExportProvider.TYPE)
                true
            }
            else -> false
        }

        private fun updateMenuItems() {
            val upcomingOnly = viewModel.upcomingOnly
            filterMenuItem?.setIcon(if (upcomingOnly) R.drawable.ic_filter_list_selected_white_24dp else R.drawable.ic_filter_list_white_24dp)
            upcomingOnlyMenuItem?.isChecked = upcomingOnly
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = BookmarksAdapter(view.context, multiChoiceHelper)
        val holder = RecyclerViewViewHolder(view).apply {
            recyclerView.apply {
                layoutManager = LinearLayoutManager(recyclerView.context)
                addItemDecoration(DividerItemDecoration(recyclerView.context, DividerItemDecoration.VERTICAL))
            }
            setAdapter(adapter)
            emptyText = getString(R.string.no_bookmark)
            isProgressBarVisible = true
        }

        viewLifecycleOwner.launchAndRepeatOnLifecycle {
            launch {
                userSettingsProvider.zoneId.collect { zoneId ->
                    adapter.zoneId = zoneId
                }
            }
            launch {
                api.roomStatuses.collect { statuses ->
                    adapter.roomStatuses = statuses
                }
            }
            launch {
                viewModel.bookmarks.filterNotNull().collect { bookmarks ->
                    adapter.submitList(bookmarks)
                    multiChoiceHelper.setAdapter(adapter, viewLifecycleOwner)
                    holder.isProgressBarVisible = false
                }
            }
        }
    }

    private fun importBookmarks(uri: Uri) {
        lifecycleScope.launchWhenStarted {
            try {
                val bookmarkIds = viewModel.readBookmarkIds(uri)
                val intent = Intent(requireContext(), ExternalBookmarksActivity::class.java)
                        .putExtra(ExternalBookmarksActivity.EXTRA_BOOKMARK_IDS, bookmarkIds)
                startActivity(intent)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                ImportBookmarksErrorDialogFragment().show(parentFragmentManager, "importBookmarksError")
            }
        }
    }

    class ImportBookmarksErrorDialogFragment : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.import_bookmarks)
                    .setMessage(R.string.import_bookmarks_error)
                    .setPositiveButton(android.R.string.ok, null)
                    .create()
        }
    }

    override fun createNfcAppData(): NdefRecord? {
        val context = context ?: return null
        val bookmarks = viewModel.bookmarks.value
        return if (bookmarks.isNullOrEmpty()) null else bookmarks.toBookmarksNfcAppData(context)
    }

    companion object {
        private const val UPCOMING_ONLY_PREF_KEY = "bookmarks_upcoming_only"
    }
}