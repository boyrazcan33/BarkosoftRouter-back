package com.barkosoft.router.service;

import com.barkosoft.router.dto.BatchResult;
import com.barkosoft.router.dto.Customer;
import com.barkosoft.router.dto.RouteOptimizationMessage;
import com.barkosoft.router.dto.RouteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class KafkaRouteConsumer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaRouteConsumer.class);

    @Autowired
    private RouteService routeService;

    @Autowired
    private JobTrackingService jobTrackingService;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000, multiplier = 2.0),
            dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = "route-optimization-requests")
    public void processBatch(RouteOptimizationMessage message,
                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                             @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                             Acknowledgment ack) {

        String jobId = message.getJobId();
        int batchIndex = message.getBatchIndex();

        logger.info("Processing batch {} for job {} (partition: {})", batchIndex, jobId, partition);

        try {
            RouteResponse batchResponse = routeService.optimizeSingleBatch(
                    message.getStartLatitude(),
                    message.getStartLongitude(),
                    message.getBatch()
            );

            double distanceKm = parseDistanceFromResponse(batchResponse.getTotalDistance());

            // Create BatchResult with geometry and mapping
            BatchResult result = new BatchResult(
                    jobId,
                    batchIndex,
                    batchResponse.getOptimizedCustomerIds(),
                    distanceKm,
                    batchResponse.getRouteGeometry(),
                    batchResponse.getCustomerGeometryMapping() // Pass mapping through
            );

            jobTrackingService.addBatchResult(result);
            ack.acknowledge();

            logger.info("Completed batch {} for job {} with {} customers, {} geometry points, and {} mappings",
                    batchIndex, jobId,
                    batchResponse.getOptimizedCustomerIds().size(),
                    batchResponse.getRouteGeometry() != null ? batchResponse.getRouteGeometry().size() : 0,
                    batchResponse.getCustomerGeometryMapping() != null ? batchResponse.getCustomerGeometryMapping().size() : 0);

        } catch (Exception e) {
            logger.error("Failed to process batch {} for job {}: {}", batchIndex, jobId, e.getMessage());

            List<Long> fallbackIds = message.getBatch().stream()
                    .map(Customer::getMyId)
                    .collect(Collectors.toList());

            BatchResult errorResult = new BatchResult();
            errorResult.setJobId(jobId);
            errorResult.setBatchIndex(batchIndex);
            errorResult.setOptimizedCustomerIds(fallbackIds);
            errorResult.setDistanceKm(0.0);
            errorResult.setRouteGeometry(null);
            errorResult.setCustomerGeometryMapping(null); // No mapping on error
            errorResult.setSuccess(false);
            errorResult.setErrorMessage(e.getMessage());

            jobTrackingService.addBatchResult(errorResult);
            ack.acknowledge();
        }
    }

    private double parseDistanceFromResponse(String totalDistance) {
        try {
            return Double.parseDouble(totalDistance.replace(" km", "").replace(",", "."));
        } catch (NumberFormatException e) {
            logger.warn("Could not parse distance: {}", totalDistance);
            return 0.0;
        }
    }
}