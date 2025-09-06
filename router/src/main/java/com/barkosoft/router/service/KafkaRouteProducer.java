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
    private static final int BATCH_SIZE = 95;

    @Autowired
    private KafkaTemplate<String, RouteOptimizationMessage> kafkaTemplate;

    @Autowired
    private JobTrackingService jobTrackingService;

    public String submitOptimizationJob(Double startLat, Double startLng, List<Customer> customers) {
        String jobId = UUID.randomUUID().toString();

        // Sort customers using nearest neighbor (Haversine)
        List<Customer> sortedCustomers = sortCustomersByNearestNeighbor(startLat, startLng, customers);

        // Create batches from sorted list
        List<List<Customer>> batches = createBatches(sortedCustomers);
        jobTrackingService.createJob(jobId, batches.size());

        logger.info("Submitting job {} with {} customers in {} batches (pre-sorted)",
                jobId, customers.size(), batches.size());

        // Send batches with proper start coordinates
        for (int i = 0; i < batches.size(); i++) {
            RouteOptimizationMessage message = new RouteOptimizationMessage();
            message.setJobId(jobId);
            message.setStartLatitude(startLat);
            message.setStartLongitude(startLng);
            message.setBatch(batches.get(i));
            message.setBatchIndex(i);
            message.setTotalBatches(batches.size());

            // Set previous batch's last customer as starting point (except for first batch)
            if (i > 0) {
                List<Customer> previousBatch = batches.get(i - 1);
                Customer lastCustomer = previousBatch.get(previousBatch.size() - 1);
                message.setPreviousBatchLastLat(lastCustomer.getLatitude());
                message.setPreviousBatchLastLng(lastCustomer.getLongitude());
            }

            int targetPartition = i % 5;
            kafkaTemplate.send(TOPIC, targetPartition, UUID.randomUUID().toString(), message);
            logger.debug("Sent batch {} for job {} to partition {}", i, jobId, targetPartition);
        }

        return jobId;
    }

    private List<Customer> sortCustomersByNearestNeighbor(Double startLat, Double startLng, List<Customer> customers) {
        if (customers.isEmpty()) {
            return new ArrayList<>();
        }

        List<Customer> remaining = new ArrayList<>(customers);
        List<Customer> sorted = new ArrayList<>();

        Double currentLat = startLat;
        Double currentLng = startLng;

        // Greedy nearest neighbor algorithm
        while (!remaining.isEmpty()) {
            Customer nearest = findNearestCustomer(currentLat, currentLng, remaining);
            sorted.add(nearest);
            remaining.remove(nearest);
            currentLat = nearest.getLatitude();
            currentLng = nearest.getLongitude();
        }

        logger.info("Sorted {} customers using nearest neighbor algorithm", sorted.size());
        return sorted;
    }

    private Customer findNearestCustomer(Double lat, Double lng, List<Customer> customers) {
        Customer nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Customer customer : customers) {
            double distance = calculateHaversineDistance(lat, lng,
                    customer.getLatitude(),
                    customer.getLongitude());
            if (distance < minDistance) {
                minDistance = distance;
                nearest = customer;
            }
        }

        return nearest;
    }

    private double calculateHaversineDistance(Double lat1, Double lng1, Double lat2, Double lng2) {
        final double R = 6371.0; // Earth radius in kilometers

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
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