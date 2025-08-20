package com.barkosoft.router.service;

import com.barkosoft.router.dto.BatchResult;
import com.barkosoft.router.dto.RouteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Service
public class JobTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(JobTrackingService.class);

    private final Map<String, JobStatus> jobStatuses = new ConcurrentHashMap<>();
    private final Map<String, RouteResponse> jobResults = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> jobLatches = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, BatchResult>> jobBatchResults = new ConcurrentHashMap<>();

    public void createJob(String jobId, int totalBatches) {
        jobStatuses.put(jobId, new JobStatus(totalBatches));
        jobBatchResults.put(jobId, new ConcurrentHashMap<>());
        jobLatches.put(jobId, new CountDownLatch(1));
        logger.info("Created job {} with {} batches", jobId, totalBatches);
    }

    public void addBatchResult(BatchResult batchResult) {
        String jobId = batchResult.getJobId();
        Map<Integer, BatchResult> batchResults = jobBatchResults.get(jobId);
        JobStatus status = jobStatuses.get(jobId);

        if (batchResults == null || status == null) {
            logger.warn("Received result for unknown job: {}", jobId);
            return;
        }

        batchResults.put(batchResult.getBatchIndex(), batchResult);
        logger.info("Received batch {} result for job {} with {} geometry points",
                batchResult.getBatchIndex(), jobId,
                batchResult.getRouteGeometry() != null ? batchResult.getRouteGeometry().size() : 0);

        // Check if all batches completed
        if (batchResults.size() == status.getTotalBatches()) {
            RouteResponse finalResponse = aggregateResults(jobId, batchResults);
            jobResults.put(jobId, finalResponse);

            CountDownLatch latch = jobLatches.get(jobId);
            if (latch != null) {
                latch.countDown();
            }
            logger.info("Job {} completed with {} customer IDs and {} geometry points",
                    jobId,
                    finalResponse.getOptimizedCustomerIds().size(),
                    finalResponse.getRouteGeometry() != null ? finalResponse.getRouteGeometry().size() : 0);
        }
    }

    public RouteResponse waitForResult(String jobId, Duration timeout) {
        CountDownLatch latch = jobLatches.get(jobId);
        if (latch == null) {
            return createErrorResponse("Job not found");
        }

        try {
            boolean completed = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                logger.warn("Job {} timed out after {} seconds", jobId, timeout.getSeconds());
                return createErrorResponse("Request timed out");
            }

            RouteResponse result = jobResults.get(jobId);
            return result != null ? result : createErrorResponse("Job failed");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Job {} was interrupted", jobId);
            return createErrorResponse("Request was interrupted");
        } finally {
            cleanup(jobId);
        }
    }

    private RouteResponse aggregateResults(String jobId, Map<Integer, BatchResult> batchResults) {
        List<Long> allCustomerIds = new ArrayList<>();
        List<List<Double>> combinedGeometry = new ArrayList<>();
        double totalDistance = 0.0;
        List<BatchResult> failedBatches = new ArrayList<>();

        // Sort by batch index to maintain order
        List<Integer> sortedIndices = new ArrayList<>(batchResults.keySet());
        sortedIndices.sort(Integer::compareTo);

        for (int i = 0; i < sortedIndices.size(); i++) {
            Integer batchIndex = sortedIndices.get(i);
            BatchResult result = batchResults.get(batchIndex);

            if (result.isSuccess()) {
                allCustomerIds.addAll(result.getOptimizedCustomerIds());
                totalDistance += result.getDistanceKm();

                // Aggregate geometry - avoid duplicate points at batch boundaries
                if (result.getRouteGeometry() != null && !result.getRouteGeometry().isEmpty()) {
                    if (i == 0) {
                        // First batch - add all points
                        combinedGeometry.addAll(result.getRouteGeometry());
                    } else {
                        // Subsequent batches - skip first point to avoid duplicate
                        if (result.getRouteGeometry().size() > 1) {
                            combinedGeometry.addAll(
                                    result.getRouteGeometry().subList(1, result.getRouteGeometry().size())
                            );
                        }
                    }
                }
            } else {
                failedBatches.add(result);
                logger.warn("Batch {} failed for job {}: {}", batchIndex, jobId, result.getErrorMessage());
            }
        }

        if (!failedBatches.isEmpty()) {
            logger.warn("Job {} had {} failed batches out of {}", jobId, failedBatches.size(), batchResults.size());
        }

        String formattedDistance = String.format("%.3f km", totalDistance).replace(".", ",");

        // Log geometry aggregation result
        logger.info("Aggregated {} geometry points for job {}", combinedGeometry.size(), jobId);

        return new RouteResponse(
                allCustomerIds,
                formattedDistance,
                combinedGeometry.isEmpty() ? null : combinedGeometry
        );
    }

    private RouteResponse createErrorResponse(String message) {
        RouteResponse response = new RouteResponse();
        response.setOptimizedCustomerIds(new ArrayList<>());
        response.setTotalDistance("0,000 km");
        response.setStatus("error: " + message);
        response.setRouteGeometry(null);
        return response;
    }

    private void cleanup(String jobId) {
        jobStatuses.remove(jobId);
        jobResults.remove(jobId);
        jobLatches.remove(jobId);
        jobBatchResults.remove(jobId);
        logger.debug("Cleaned up job {}", jobId);
    }

    private static class JobStatus {
        private final int totalBatches;
        private final long createdAt;

        public JobStatus(int totalBatches) {
            this.totalBatches = totalBatches;
            this.createdAt = System.currentTimeMillis();
        }

        public int getTotalBatches() {
            return totalBatches;
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }
}