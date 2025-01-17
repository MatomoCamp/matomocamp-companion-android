package org.matomocamp.companion.activities

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import org.matomocamp.companion.R
import org.matomocamp.companion.db.ScheduleDao
import org.matomocamp.companion.fragments.PersonInfoListFragment
import org.matomocamp.companion.model.Person
import org.matomocamp.companion.utils.configureToolbarColors
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PersonInfoActivity : AppCompatActivity(R.layout.person_info) {

    @Inject
    lateinit var scheduleDao: ScheduleDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(findViewById(R.id.toolbar))

        val person: Person = intent.getParcelableExtra(EXTRA_PERSON)!!

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = person.name

        findViewById<View>(R.id.fab).setOnClickListener {
            openPersonDetails(person)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add<PersonInfoListFragment>(R.id.content,
                    args = PersonInfoListFragment.createArguments(person))
            }
        }
    }

    private fun openPersonDetails(person: Person) {
        val context = this
        lifecycleScope.launchWhenStarted {
            person.getUrl(scheduleDao.getYear())?.let { url ->
                try {
                    CustomTabsIntent.Builder()
                        .configureToolbarColors(context, R.color.light_color_primary)
                        .setStartAnimations(context, R.anim.slide_in_right, R.anim.slide_out_left)
                        .setExitAnimations(context, R.anim.slide_in_left, R.anim.slide_out_right)
                        .build()
                        .launchUrl(context, Uri.parse(url))
                } catch (ignore: ActivityNotFoundException) {
                }
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