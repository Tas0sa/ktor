@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.ktor.test.dispatcher

import kotlinx.coroutines.*
import platform.Foundation.*
import platform.posix.*
import kotlin.coroutines.*
import kotlin.native.concurrent.*
import kotlin.system.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * Amount of time any task is processed and can't be rescheduled.
 */
private const val TIME_QUANTUM = 0.01

private var TEST_WORKER: Worker = createTestWorker()
private val SLEEP_TIME: UInt = 10.milliseconds.inWholeMicroseconds.toUInt()

/**
 * Test runner for native suspend tests.
 */
public actual fun testSuspend(
    context: CoroutineContext,
    timeout: Long,
    block: suspend CoroutineScope.() -> Unit
) {
    executeInWorker(timeout) {
        runBlocking {
            val loop = ThreadLocalEventLoop.currentOrNull()!!

            val task = launch { block() }
            while (!task.isCompleted) {
                val date = NSDate().addTimeInterval(TIME_QUANTUM) as NSDate
                NSRunLoop.mainRunLoop.runUntilDate(date)

                loop.processNextEvent()
            }
        }
    }
}

private fun executeInWorker(timeout: Long, block: () -> Unit) {
    val result = TEST_WORKER.execute(TransferMode.UNSAFE, { block }) {
        it()
    }

    val endTime = getTimeMillis() + timeout
    while (result.state == FutureState.SCHEDULED && endTime > getTimeMillis()) {
        usleep(SLEEP_TIME)
    }

    when (result.state) {
        FutureState.SCHEDULED -> {
            TEST_WORKER.requestTermination(processScheduledJobs = false)
            TEST_WORKER = createTestWorker()
            error("Test is timed out")
        }
        else -> {
            result.consume { }
        }
    }
}

private fun createTestWorker(): Worker = Worker.start(
    name = "Ktor Test Worker",
    errorReporting = true
)
