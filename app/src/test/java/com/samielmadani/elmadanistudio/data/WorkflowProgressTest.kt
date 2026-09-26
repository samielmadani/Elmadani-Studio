package com.samielmadani.elmadanistudio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkflowProgressTest {
    @Test
    fun percentageTracksCompletedSteps() {
        assertEquals(25, calculateWorkflowPercent(listOf("completed", "in_progress", "queued", "queued")))
        assertEquals(50, calculateWorkflowPercent(listOf("completed", "completed", "in_progress", "queued")))
    }

    @Test
    fun unknownStepTotalIsIndeterminate() {
        assertNull(calculateWorkflowPercent(emptyList()))
    }
}
