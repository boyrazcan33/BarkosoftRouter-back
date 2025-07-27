package com.barkosoft.router.service;

import com.barkosoft.router.dto.Customer;
import com.barkosoft.router.dto.RouteOptimizationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class KafkaRouteProducer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaRouteProducer.class);
    private static final String TOPIC = "route-optimization-requests";
    private static final int BATCH_SIZE = 50;

    @Autowired
    private KafkaTemplate<String, RouteOptimizationMessage> kafkaTemplate;

    @Autowired
    private JobTrackingService jobTrackingService;

    public String submitOptimizationJob(Double startLat, Double startLng, List<Customer> customers) {
        String jobId = UUID.randomUUID().toString();

        List<List<Customer>> batches = createBatches(customers);
        jobTrackingService.createJob(jobId, batches.size());

        logger.info("Submitting job {} with {} customers in {} batches", jobId, customers.size(), batches.size());

        for (int i = 0; i < batches.size(); i++) {
            RouteOptimizationMessage message = new RouteOptimizationMessage(
                    jobId, startLat, startLng, batches.get(i), i, batches.size()
            );

            kafkaTemplate.send(TOPIC, jobId, message);
            logger.debug("Sent batch {} for job {}", i, jobId);
        }

        return jobId;
    }

    private List<List<Customer>> createBatches(List<Customer> customers) {
        List<List<Customer>> batches = new ArrayList<>();

        for (int i = 0; i < customers.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, customers.size());
            batches.add(new ArrayList<>(customers.subList(i, endIndex)));
        }

        return batches;
    }
}