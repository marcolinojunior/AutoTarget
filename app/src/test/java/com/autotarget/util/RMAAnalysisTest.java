package com.autotarget.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class RMAAnalysisTest {

    @Test
    public void testRecordExecutionAndRuntimeReport() {
        RMAAnalysis.resetRuntimeStats();
        RMAAnalysis.recordExecution("T1-Physics", 5);
        RMAAnalysis.recordExecution("T1-Physics", 9);
        RMAAnalysis.recordExecution("T7-Sensor", 2);

        String report = RMAAnalysis.getRuntimeMetricsReport();
        assertNotNull(report);
        assertTrue(report.contains("task,executions"));
        assertTrue(report.contains("T1-Physics"));
        assertTrue(report.contains("T7-Sensor"));
    }

    @Test
    public void testTaskTableSummaryContainsJitterAndDeps() {
        String summary = RMAAnalysis.getTaskTableSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Ji"));
        assertTrue(summary.contains("deps"));
        assertTrue(summary.contains("T8-Reconciliacao"));
    }
}
