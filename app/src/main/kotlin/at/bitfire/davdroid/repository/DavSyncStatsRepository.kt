/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.content.Context
import android.content.pm.PackageManager
import at.bitfire.davdroid.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.util.logging.Logger
import javax.inject.Inject

class DavSyncStatsRepository @Inject constructor(
    @ApplicationContext val context: Context,
    db: AppDatabase,
    private val logger: Logger
) {

    private val dao = db.syncStatsDao()

    data class LastSynced(
        val appName: String,
        val lastSynced: Long
    )
    fun getLastSyncedFlow(collectionId: Long): Flow<List<LastSynced>> =
        dao.getByCollectionIdFlow(collectionId).map { list ->
            val collator = Collator.getInstance()
            list.map { stats ->
                LastSynced(
                    appName = appNameFromAuthority(stats.authority),
                    lastSynced = stats.lastSync
                )
            }.sortedWith { a, b ->
                collator.compare(a.appName, b.appName)
            }
        }


    /**
     * Tries to find the application name for given authority. Returns the authority if not
     * found.
     *
     * @param authority authority to find the application name for (ie "at.techbee.jtx")
     * @return the application name of authority (ie "jtx Board")
     */
    private fun appNameFromAuthority(authority: String): String {
        val packageManager = context.packageManager
        val packageName = packageManager.resolveContentProvider(authority, 0)?.packageName ?: authority
        return try {
            val appInfo = packageManager.getPackageInfo(packageName, 0).applicationInfo
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            logger.warning("Application name not found for authority: $authority")
            authority
        }
    }

}