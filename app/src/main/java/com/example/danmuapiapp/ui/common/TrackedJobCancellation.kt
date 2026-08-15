package com.example.danmuapiapp.ui.common

import kotlinx.coroutines.Job

internal fun <K> MutableMap<K, Job>.cancelTrackedJobs() {
    val jobsToCancel = values.toList()
    clear()
    // Cancellation handlers may synchronously remove their own tracking entry.
    jobsToCancel.forEach { it.cancel() }
}
