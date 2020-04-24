/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import androidx.annotation.WorkerThread
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.AppDatabase
import at.bitfire.davdroid.model.Service
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.ical4android.TaskProvider.ProviderName.OpenTasks
import kotlin.concurrent.thread

class OpenTasksWatcher(
        context: Context
): PackageChangedReceiver(context) {

    companion object {

        @WorkerThread
        fun updateTaskSync(context: Context) {
            val tasksInstalled = LocalTaskList.tasksProviderAvailable(context)
            Logger.log.info("App was launched or package was (in)installed; OpenTasks provider now available = $tasksInstalled")

            // check all accounts and (de)activate OpenTasks if a CalDAV service is defined
            val db = AppDatabase.getInstance(context)
            db.serviceDao().getByType(Service.TYPE_CALDAV).forEach { service ->
                val account = Account(service.accountName, context.getString(R.string.account_type))
                val currentSyncable = ContentResolver.getIsSyncable(account, OpenTasks.authority)
                var enabledAnyAccount = false
                if (tasksInstalled) {
                    if (currentSyncable <= 0) {
                        Logger.log.info("Enabling OpenTasks sync for $account")
                        ContentResolver.setIsSyncable(account, OpenTasks.authority, 1)
                        AccountSettings(context, account).setSyncInterval(OpenTasks.authority, Constants.DEFAULT_SYNC_INTERVAL)
                        enabledAnyAccount = true
                    }
                } else if (currentSyncable != 0) {
                    Logger.log.info("Disabling OpenTasks sync for $account")
                    ContentResolver.setIsSyncable(account, OpenTasks.authority, 0)
                }

                if (enabledAnyAccount && !PermissionUtils.havePermissions(context, PermissionUtils.TASKS_PERMISSIONS)) {
                    Logger.log.warning("Tasks sync is now enabled for at least one account, but OpenTasks permissions are not granted")
                    PermissionUtils.notifyPermissions(context, null)
                }
            }
        }

    }


    override fun onReceive(context: Context, intent: Intent) {
        thread {
            updateTaskSync(context)
        }
    }

}