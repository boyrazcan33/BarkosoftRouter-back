package com.barkosoft.router.service;

import com.barkosoft.router.dto.BatchResult;
import com.barkosoft.router.dto.RouteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class JobTrackingServiceTest {

    private JobTrackingService jobTrackingService;

    @BeforeEach
    void setUp() {
        jobTrackingService = new JobTrackingService();
    }

    @Test
    void shouldCreateAndTrackJob() {
        String jobId = "test-job-1";
        int totalBatches = 2;

        jobTrackingService.createJob(jobId, totalBatches);

        RouteResponse response = jobTrackingService.waitForResult(jobId, Duration.ofSeconds(1));

        assertNotNull(response);
        assertTrue(response.getStatus().startsWith("error"));
    }

    @Test
    void shouldAggregateResults() {
        String jobId = "test-job-2";
        jobTrackingService.createJob(jobId, 2);

        BatchResult batch1 = new BatchResult();
        batch1.setJobId(jobId);
        batch1.setBatchIndex(0);
        batch1.setOptimizedCustomerIds(Arrays.asList(1L, 2L));
        batch1.setDistanceKm(5.5);
        batch1.setSuccess(true);

        BatchResult batch2 = new BatchResult();
        batch2.setJobId(jobId);
        batch2.setBatchIndex(1);
        batch2.setOptimizedCustomerIds(Arrays.asList(3L, 4L));
        batch2.setDistanceKm(3.2);
        batch2.setSuccess(true);

        jobTrackingService.addBatchResult(batch1);
        jobTrackingService.addBatchResult(batch2);

        RouteResponse result = jobTrackingService.waitForResult(jobId, Duration.ofSeconds(5));

        assertNotNull(result);
        assertEquals(4, result.getOptimizedCustomerIds().size());
        assertTrue(result.getTotalDistance().contains("8,700"));
    }

    @Test
    void shouldHandlePartialFailure() {
        String jobId = "test-job-3";
        jobTrackingService.createJob(jobId, 2);

        BatchResult successBatch = new BatchResult();
        successBatch.setJobId(jobId);
        successBatch.setBatchIndex(0);
        successBatch.setOptimizedCustomerIds(Arrays.asList(1L, 2L));
        successBatch.setDistanceKm(5.0);
        successBatch.setSuccess(true);

        BatchResult failedBatch = new BatchResult();
        failedBatch.setJobId(jobId);
        failedBatch.setBatchIndex(1);
        failedBatch.setOptimizedCustomerIds(Arrays.asList(3L, 4L));
        failedBatch.setDistanceKm(0.0);
        failedBatch.setSuccess(false);
        failedBatch.setErrorMessage("API Error");

        jobTrackingService.addBatchResult(successBatch);
        jobTrackingService.addBatchResult(failedBatch);

        RouteResponse result = jobTrackingService.waitForResult(jobId, Duration.ofSeconds(5));

        assertNotNull(result);
        // Even with failure, we should get customer IDs from failed batch
        assertEquals(2, result.getOptimizedCustomerIds().size()); // Only successful batch customers
    }

    @Test
    void shouldTimeout() {
        String jobId = "test-job-4";
        jobTrackingService.createJob(jobId, 1);

        RouteResponse result = jobTrackingService.waitForResult(jobId, Duration.ofMillis(100));

        assertNotNull(result);
        assertTrue(result.getStatus().contains("timeout") || result.getStatus().contains("timed out"));
    }
}