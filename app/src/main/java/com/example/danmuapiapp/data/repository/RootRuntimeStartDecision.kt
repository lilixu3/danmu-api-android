package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.service.RootRuntimeController

internal fun shouldForceNewRootStartedAt(outcome: RootRuntimeController.StartOutcome): Boolean {
    return outcome == RootRuntimeController.StartOutcome.StartedNewProcess
}
