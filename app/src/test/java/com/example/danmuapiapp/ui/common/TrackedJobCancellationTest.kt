package com.example.danmuapiapp.ui.common

import kotlinx.coroutines.Job
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedJobCancellationTest {

    @Test
    fun `cancel tolerates completion handlers removing map entries`() {
        val trackedJobs = linkedMapOf<String, Job>()
        val jobs = List(3) { index ->
            Job().also { job ->
                val key = "job-$index"
                trackedJobs[key] = job
                job.invokeOnCompletion { trackedJobs.remove(key) }
            }
        }

        trackedJobs.cancelTrackedJobs()

        assertTrue(trackedJobs.isEmpty())
        assertTrue(jobs.all { it.isCancelled })
    }
}
