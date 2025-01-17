/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.os.Build
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.resource.LocalEvent
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.Event
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import org.junit.Assert.assertNotNull
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.util.logging.Logger

/**
 * JUnit ClassRule which initializes the AOSP CalendarProvider.
 * Needed for some "flaky" tests which would otherwise only succeed on second run.
 *
 * Currently tested on development machine (Ryzen) with Android 12 images (with/without Google Play).
 * Calendar provider behaves quite randomly, so it may or may not work. If you (the reader
 * if this comment) can find out on how to initialize the calendar provider so that the
 * tests are reliably run after `adb shell pm clear com.android.providers.calendar`,
 * please let us know!
 *
 * If you run tests manually, just make sure to ignore the first run after the calendar
 * provider has been accessed the first time.
 *
 * See [at.bitfire.davdroid.resource.LocalCalendarTest] for how to use this rule.
 */
class InitCalendarProviderRule private constructor(): ExternalResource() {

    companion object {

        private var isInitialized = false
        private val logger = Logger.getLogger(InitCalendarProviderRule::javaClass.name)

        fun getInstance(): RuleChain = RuleChain
            .outerRule(GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            .around(InitCalendarProviderRule())

    }

    override fun before() {
        if (!isInitialized) {
            logger.info("Initializing calendar provider")
            if (Build.VERSION.SDK_INT < 31)
                logger.warning("Calendar provider initialization may or may not work. See InitCalendarProviderRule")

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val client = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)
            assertNotNull("Couldn't acquire calendar provider", client)

            client!!.use {
                initCalendarProvider(client)
                isInitialized = true
            }
        }
    }

    private fun initCalendarProvider(provider: ContentProviderClient) {
        val account = Account("LocalCalendarTest", CalendarContract.ACCOUNT_TYPE_LOCAL)
        val uri = AndroidCalendar.create(account, provider, ContentValues())
        val calendar = AndroidCalendar.findByID(
            account,
            provider,
            LocalCalendar.Factory,
            ContentUris.parseId(uri)
        )
        try {
            // single event init
            val normalEvent = Event().apply {
                dtStart = DtStart("20220120T010203Z")
                summary = "Event with 1 instance"
            }
            val normalLocalEvent = LocalEvent(calendar, normalEvent, null, null, null, 0)
            normalLocalEvent.add()
            LocalEvent.numInstances(provider, account, normalLocalEvent.id!!)

            // recurring event init
            val recurringEvent = Event().apply {
                dtStart = DtStart("20220120T010203Z")
                summary = "Event over 22 years"
                rRules.add(RRule("FREQ=YEARLY;UNTIL=20740119T010203Z"))     // year needs to be  >2074 (not supported by Android <11 Calendar Storage)
            }
            val localRecurringEvent = LocalEvent(calendar, recurringEvent, null, null, null, 0)
            localRecurringEvent.add()
            LocalEvent.numInstances(provider, account, localRecurringEvent.id!!)
        } finally {
            calendar.delete()
        }
    }

}