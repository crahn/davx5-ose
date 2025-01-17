/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.account.TestAccountAuthenticator
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidTest
class SyncerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var accountSettingsFactory: AccountSettings.Factory

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var testSyncer: Provider<TestSyncer>

    /** (ab)use our WebDAV provider as a mock provider because it's our own and we don't need any permissions for it */
    private val mockAuthority by lazy { context.getString(at.bitfire.davdroid.R.string.webdav_authority) }

    lateinit var account: Account

    @Before
    fun setUp() {
        hiltRule.inject()

        account = TestAccountAuthenticator.create()
    }

    @After
    fun tearDown() {
        TestAccountAuthenticator.remove(account)
    }


    @Test
    fun testOnPerformSync_runsSyncAndSetsClassLoader() {
        val syncer = testSyncer.get()
        syncer.onPerformSync(account, arrayOf(), mockAuthority, SyncResult())

        // check whether onPerformSync() actually calls sync()
        assertEquals(1, syncer.syncCalled.get())

        // check whether contextClassLoader is set
        assertEquals(context.classLoader, Thread.currentThread().contextClassLoader)
    }


    class TestSyncer @Inject constructor() : Syncer() {

        val syncCalled = AtomicInteger()

        override fun sync(
            account: Account,
            extras: Array<String>,
            authority: String,
            httpClient: Lazy<HttpClient>,
            provider: ContentProviderClient,
            syncResult: SyncResult
        ) {
            syncCalled.incrementAndGet()
        }

    }

}