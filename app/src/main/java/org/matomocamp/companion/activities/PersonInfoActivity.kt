package org.matomocamp.companion.activities

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.add
import androidx.fragment.app.commit
import org.matomocamp.companion.R
import org.matomocamp.companion.fragments.PersonInfoListFragment
import org.matomocamp.companion.model.Person
import org.matomocamp.companion.utils.DateUtils
import org.matomocamp.companion.utils.configureToolbarColors
import org.matomocamp.companion.viewmodels.PersonInfoViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PersonInfoActivity : AppCompatActivity(R.layout.person_info) {

    private val viewModel: PersonInfoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(findViewById(R.id.toolbar))

        val person: Person = intent.getParcelableExtra(EXTRA_PERSON)!!
        viewModel.setPerson(person)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = person.name

        findViewById<View>(R.id.fab).setOnClickListener {
            // Look for the first non-placeholder event in the paged list
            val statusEvent = viewModel.events.value?.firstOrNull { it != null }
            if (statusEvent != null) {
                val url = person.getUrl()
                try {
                    CustomTabsIntent.Builder()
                            .configureToolbarColors(this, R.color.light_color_primary)
                            .build()
                            .launchUrl(this, Uri.parse(url))
                } catch (ignore: ActivityNotFoundException) {
                }
            }
        }

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add<PersonInfoListFragment>(R.id.content)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_PERSON = "person"
    }
}