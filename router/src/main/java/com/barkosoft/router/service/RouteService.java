package com.barkosoft.router.service;

import com.barkosoft.router.dto.OSRMResponse;
import com.barkosoft.router.dto.RouteResponse;
import com.barkosoft.router.dto.Customer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RouteService {

    private static final Logger logger = LoggerFactory.getLogger(RouteService.class);
    private static final int BATCH_SIZE = 50; // Safety limit

    @Value("${osrm.base.url:http://router.project-osrm.org}")
    private String osrmBaseUrl;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public RouteService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public RouteResponse optimizeRoute(Double startLat, Double startLng, List<Customer> customers) {
        if (customers.isEmpty()) {
            logger.warn("No customers provided in request");
            return new RouteResponse(new ArrayList<>(), "0,000 km");
        }

        // If customers <= 50, use original logic
        if (customers.size() <= BATCH_SIZE) {
            return optimizeSingleBatch(startLat, startLng, customers);
        }

        // For large datasets, use geographical clustering
        return optimizeWithClustering(startLat, startLng, customers);
    }

    private RouteResponse optimizeSingleBatch(Double startLat, Double startLng, List<Customer> customers) {
        StringBuilder coordinates = new StringBuilder();
        coordinates.append(String.format("%f,%f", startLng, startLat));

        for (Customer customer : customers) {
            coordinates.append(";").append(String.format("%f,%f", customer.getLongitude(), customer.getLatitude()));
        }

        try {
            String url = String.format("%s/trip/v1/driving/%s?source=first&roundtrip=false",
                    osrmBaseUrl, coordinates.toString());

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(120))
                    .block();

            return parseOptimizedRouteFromResponse(response, customers);

        } catch (Exception e) {
            logger.error("OSRM API call failed: {}", e.getMessage());
            throw new RuntimeException("Route optimization failed: " + e.getMessage());
        }
    }

    private RouteResponse optimizeWithClustering(Double startLat, Double startLng, List<Customer> customers) {
        logger.info("Processing {} customers with clustering approach", customers.size());

        // Simple geographical clustering - group customers by proximity
        List<List<Customer>> clusters = createGeographicalClusters(customers, BATCH_SIZE);

        List<Long> allOptimizedIds = new ArrayList<>();
        double totalDistance = 0.0;

        // Process each cluster
        for (int i = 0; i < clusters.size(); i++) {
            List<Customer> cluster = clusters.get(i);
            logger.info("Processing cluster {} with {} customers", i + 1, cluster.size());

            try {
                RouteResponse clusterResponse = optimizeSingleBatch(startLat, startLng, cluster);
                allOptimizedIds.addAll(clusterResponse.getOptimizedCustomerIds());

                // Parse distance (remove "km" and convert comma to dot)
                String distanceStr = clusterResponse.getTotalDistance().replace(" km", "").replace(",", ".");
                totalDistance += Double.parseDouble(distanceStr);

            } catch (Exception e) {
                logger.error("Failed to optimize cluster {}: {}", i + 1, e.getMessage());
                // Add cluster customers in original order as fallback
                allOptimizedIds.addAll(cluster.stream().map(Customer::getMyId).collect(Collectors.toList()));
            }
        }

        return new RouteResponse(allOptimizedIds, String.format("%.3f km", totalDistance).replace(".", ","));
    }

    // Simple clustering: group customers by latitude bands
    // TODO: Could be improved with proper K-means clustering for better geographical distribution
    private List<List<Customer>> createGeographicalClusters(List<Customer> customers, int maxClusterSize) {
        List<List<Customer>> clusters = new ArrayList<>();

        // Sort customers by latitude first, then longitude
        List<Customer> sortedCustomers = customers.stream()
                .sorted((c1, c2) -> {
                    int latCompare = Double.compare(c1.getLatitude(), c2.getLatitude());
                    if (latCompare != 0) return latCompare;
                    return Double.compare(c1.getLongitude(), c2.getLongitude());
                })
                .collect(Collectors.toList());

        // Create clusters of max size
        for (int i = 0; i < sortedCustomers.size(); i += maxClusterSize) {
            int endIndex = Math.min(i + maxClusterSize, sortedCustomers.size());
            clusters.add(new ArrayList<>(sortedCustomers.subList(i, endIndex)));
        }

        logger.info("Created {} clusters from {} customers", clusters.size(), customers.size());
        return clusters;
    }

    private RouteResponse parseOptimizedRouteFromResponse(String jsonResponse, List<Customer> customers) {
        try {
            OSRMResponse response = objectMapper.readValue(jsonResponse, OSRMResponse.class);

            if (!"Ok".equals(response.getCode())) {
                throw new RuntimeException("OSRM optimization failed: " + response.getCode());
            }

            Map<String, Object> trip = response.getTrips().get(0);
            Double totalDistanceKm = ((Number) trip.get("distance")).doubleValue() / 1000.0;

            List<Long> optimizedRoute = new ArrayList<>();
            for (int i = 1; i < response.getWaypoints().size(); i++) {
                Map<String, Object> waypoint = response.getWaypoints().get(i);
                Integer waypointIndex = (Integer) waypoint.get("waypoint_index");
                if (waypointIndex != null) {
                    int customerIndex = waypointIndex - 1;
                    if (customerIndex >= 0 && customerIndex < customers.size()) {
                        optimizedRoute.add(customers.get(customerIndex).getMyId());
                    }
                }
            }

            return new RouteResponse(optimizedRoute, String.format("%.3f km", totalDistanceKm).replace(".", ","));

        } catch (Exception e) {
            logger.error("Failed to parse OSRM response: {}", e.getMessage());
            throw new RuntimeException("Route optimization failed: " + e.getMessage());
        }
    }
}