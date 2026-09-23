package me.timeto.shared

import kotlinx.cinterop.UnsafeNumber
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.WatchConnectivity.WCSession
import me.timeto.shared.db.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * I use application context for backup because of limits:
 * https://stackoverflow.com/a/35076706/5169420
 * I mean the option - send a request and immediately get a backup
 * does not work, but it is ok, because for responsive UI data are
 * updated locally on the watch and only then synchronized with iPhone.
 *
 * While using the app, I ran into a limit of 65.5 KB. This is mainly
 * for the history of the interval for hints. 262.1 KB should be enough,
 * we can look for optimization.
 */
object WatchToIosSync {

    private const val LOCAL_DELAY_MLS = 300L

    fun sync() {
        requestFromAppleWatch(
            command = "sync",
            jData = JsonObject(mapOf()),
        )
    }

    fun startIntervalWithLocal(
        activityDb: ActivityDb,
        timer: Int?,
    ): Unit = launchExIo {
        // todo use with local updates
        /*
        val intervalDb: IntervalDb = IntervalDb.insertWithValidation(
            timer = seconds,
            goalDb = activeGoalDb,
            note = intervalDb.note,
        )
        */
        launchEx {
            // todo use with local updates
            // delay(LOCAL_DELAY_MLS)
            val map = mapOf(
                "activity_id" to JsonPrimitive(activityDb.id),
                "timer" to JsonPrimitive(timer),
                "note" to JsonNull,
            )
            requestFromAppleWatch(
                command = "start_interval",
                jData = JsonObject(map)
            )
        }
    }

    fun startTaskWithLocal(
        taskDb: TaskDb,
        activityDb: ActivityDb,
        // todo timerType
        timer: Int?,
    ): Unit = launchExIo {
        // todo local
        // task.startInterval(timer, goalDb)
        launchEx {
            // todo use with local updates
            // delay(LOCAL_DELAY_MLS)
            val map = mapOf(
                "task_id" to JsonPrimitive(taskDb.id),
                "activity_id" to JsonPrimitive(activityDb.id),
                "timer" to JsonPrimitive(timer),
            )
            requestFromAppleWatch(
                command = "start_task",
                jData = JsonObject(map)
            )
        }
    }

    fun togglePomodoro() {
        launchExIo {
            val map = mapOf<String, JsonPrimitive>()
            requestFromAppleWatch(
                command = "toggle_pomodoro",
                jData = JsonObject(map)
            )
        }
    }

    ///
    /// Smart Restore

    fun smartRestore(
        jsonString: String,
    ): Unit = launchExIo {
        SmartRestore.restore(jsonString)
    }
}

@OptIn(UnsafeNumber::class)
private fun requestFromAppleWatch(
    command: String,
    jData: JsonElement,
    errDelayMls: Long = 5_000L,
    onResponse: ((String) -> Unit)? = null,
) {
    if (!WCSession.isSupported())
        return

    val jRequest = JsonObject(
        mapOf(
            "command" to JsonPrimitive(command),
            "data" to jData,
        )
    )
    val requestString = jRequest.toString() as NSString
    val requestData = requestString.dataUsingEncoding(NSUTF8StringEncoding)
    if (requestData == null) {
        reportApi("requestFromAppleWatch() REQUEST data is null\n$requestString")
        return
    }

    var isResponseReceived = false
    WCSession.defaultSession.sendMessageData(
        requestData,
        replyHandler = { responseData ->
            isResponseReceived = true

            if (responseData == null) {
                reportApi("requestFromAppleWatch() RESPONSE data is null\n$command\n$requestString")
                return@sendMessageData
            }

            onResponse?.invoke(NSString.create(responseData, NSUTF8StringEncoding) as String)
        },
        errorHandler = { error ->
            launchExIo {
                reportApi("requestFromAppleWatch() errorHandler:\n${error?.localizedDescription}")
                // showUiAlert(error?.localizedDescription ?: "Internal Error")
            }
        }
    )
    launchExIo {
        delay(errDelayMls.milliseconds)
        if (!isResponseReceived) {
            zlog("Sync Error")
            // showUiAlert("Sync Error") // todo
        }
    }
}
