package com.samielmadani.elmadanistudio.data

data class WorkflowProgress(val percent: Int?)

data class WorkflowProgressScan(
    val checkedRepos: Set<String>,
    val activeRuns: Map<String, WorkflowProgress>
)

internal fun calculateWorkflowPercent(stepStatuses: Iterable<String>): Int? {
    var totalSteps = 0
    var completedSteps = 0
    for (status in stepStatuses) {
        totalSteps++
        if (status == "completed") completedSteps++
    }
    return if (totalSteps == 0) null else (completedSteps * 100 / totalSteps).coerceIn(0, 100)
}
