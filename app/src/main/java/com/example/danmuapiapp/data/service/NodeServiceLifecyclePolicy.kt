package com.example.danmuapiapp.data.service

internal fun shouldStopServiceAfterRejectedStart(
    serviceStopRequested: Boolean,
    running: Boolean,
    stopping: Boolean,
    threadAlive: Boolean
): Boolean {
    return serviceStopRequested && !running && !stopping && !threadAlive
}

internal fun shouldReportUnexpectedNodeServiceDestroy(
    serviceStopRequested: Boolean,
    stopping: Boolean,
    desiredRunning: Boolean,
    running: Boolean,
    threadAlive: Boolean,
    startupStarted: Boolean
): Boolean {
    val hadActiveRuntime = running || stopping || threadAlive || startupStarted
    val controlledStop = serviceStopRequested || stopping || !desiredRunning
    return hadActiveRuntime && !controlledStop
}
