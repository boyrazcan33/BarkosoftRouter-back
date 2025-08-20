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
            return new RouteResponse(new ArrayList<>(), "0,000 km", null);
        }

        // If customers <= 50, use single batch
        if (customers.size() <= BATCH_SIZE) {
            return optimizeSingleBatch(startLat, startLng, customers);
        }

        // For large datasets, use simple batching (Kafka handles this better now)
        return optimizeWithBatching(startLat, startLng, customers);
    }

    public RouteResponse optimizeSingleBatch(Double startLat, Double startLng, List<Customer> customers) {
        StringBuilder coordinates = new StringBuilder();
        coordinates.append(String.format("%f,%f", startLng, startLat));

        for (Customer customer : customers) {
            coordinates.append(";").append(String.format("%f,%f", customer.getLongitude(), customer.getLatitude()));
        }

        try {
            // First, get the optimized route order using Trip API
            String tripUrl = String.format("%s/trip/v1/driving/%s?source=first&roundtrip=false",
                    osrmBaseUrl, coordinates.toString());

            logger.info("Making OSRM Trip request for {} customers", customers.size());

            String tripResponse = webClient.get()
                    .uri(tripUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            RouteResponse optimizedRoute = parseOptimizedRouteFromResponse(tripResponse, customers);

            // Now get the actual road geometry using Route API
            List<List<Double>> geometry = fetchRouteGeometry(startLat, startLng, customers, optimizedRoute.getOptimizedCustomerIds());

            // Return response with geometry
            return new RouteResponse(
                    optimizedRoute.getOptimizedCustomerIds(),
                    optimizedRoute.getTotalDistance(),
                    geometry
            );

        } catch (Exception e) {
            logger.error("OSRM API call failed for {} customers: {}", customers.size(), e.getMessage());
            throw new RuntimeException("Route optimization failed: " + e.getMessage());
        }
    }

    private List<List<Double>> fetchRouteGeometry(Double startLat, Double startLng,
                                                  List<Customer> customers,
                                                  List<Long> optimizedCustomerIds) {
        try {
            // Create customer lookup map
            Map<Long, Customer> customerMap = customers.stream()
                    .collect(Collectors.toMap(Customer::getMyId, c -> c));

            // Build coordinates in optimized order
            StringBuilder orderedCoordinates = new StringBuilder();
            orderedCoordinates.append(String.format("%f,%f", startLng, startLat));

            for (Long customerId : optimizedCustomerIds) {
                Customer customer = customerMap.get(customerId);
                if (customer != null) {
                    orderedCoordinates.append(";")
                            .append(String.format("%f,%f", customer.getLongitude(), customer.getLatitude()));
                }
            }

            // Call OSRM Route API for geometry
            String routeUrl = String.format("%s/route/v1/driving/%s?geometries=geojson&overview=full",
                    osrmBaseUrl, orderedCoordinates.toString());

            logger.info("Fetching route geometry for optimized path");

            String routeResponse = webClient.get()
                    .uri(routeUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            return parseGeometryFromRouteResponse(routeResponse);

        } catch (Exception e) {
            logger.error("Failed to fetch route geometry: {}", e.getMessage());
            return null; // Return null if geometry fetch fails
        }
    }

    @SuppressWarnings("unchecked")
    private List<List<Double>> parseGeometryFromRouteResponse(String jsonResponse) {
        try {
            Map<String, Object> response = objectMapper.readValue(jsonResponse, Map.class);

            if (!"Ok".equals(response.get("code"))) {
                logger.warn("OSRM Route API returned non-OK status: {}", response.get("code"));
                return null;
            }

            List<Map<String, Object>> routes = (List<Map<String, Object>>) response.get("routes");
            if (routes == null || routes.isEmpty()) {
                logger.warn("No routes found in OSRM response");
                return null;
            }

            Map<String, Object> route = routes.get(0);
            Map<String, Object> geometry = (Map<String, Object>) route.get("geometry");

            if (geometry == null || !geometry.containsKey("coordinates")) {
                logger.warn("No geometry found in route");
                return null;
            }

            List<List<Double>> coordinates = (List<List<Double>>) geometry.get("coordinates");
            logger.info("Retrieved {} geometry points from OSRM", coordinates.size());

            return coordinates;

        } catch (Exception e) {
            logger.error("Failed to parse geometry from OSRM response: {}", e.getMessage());
            return null;
        }
    }

    private RouteResponse optimizeWithBatching(Double startLat, Double startLng, List<Customer> customers) {
        logger.info("Processing {} customers with simple batching", customers.size());

        List<List<Customer>> batches = createSimpleBatches(customers, BATCH_SIZE);
        List<Long> allOptimizedIds = new ArrayList<>();
        List<List<Double>> combinedGeometry = new ArrayList<>();
        double totalDistance = 0.0;

        Double lastLat = startLat;
        Double lastLng = startLng;

        for (int i = 0; i < batches.size(); i++) {
            List<Customer> batch = batches.get(i);
            logger.info("Processing batch {} with {} customers", i + 1, batch.size());

            try {
                RouteResponse batchResponse = optimizeSingleBatch(lastLat, lastLng, batch);
                allOptimizedIds.addAll(batchResponse.getOptimizedCustomerIds());

                // Append geometry (skip first point of subsequent batches to avoid duplicates)
                if (batchResponse.getRouteGeometry() != null) {
                    if (i == 0) {
                        combinedGeometry.addAll(batchResponse.getRouteGeometry());
                    } else if (batchResponse.getRouteGeometry().size() > 1) {
                        combinedGeometry.addAll(batchResponse.getRouteGeometry().subList(1, batchResponse.getRouteGeometry().size()));
                    }
                }

                String distanceStr = batchResponse.getTotalDistance().replace(" km", "").replace(",", ".");
                totalDistance += Double.parseDouble(distanceStr);

                // Update last position for next batch
                if (!batch.isEmpty() && !batchResponse.getOptimizedCustomerIds().isEmpty()) {
                    Long lastCustomerId = batchResponse.getOptimizedCustomerIds().get(batchResponse.getOptimizedCustomerIds().size() - 1);
                    Customer lastCustomer = batch.stream()
                            .filter(c -> c.getMyId().equals(lastCustomerId))
                            .findFirst()
                            .orElse(null);
                    if (lastCustomer != null) {
                        lastLat = lastCustomer.getLatitude();
                        lastLng = lastCustomer.getLongitude();
                    }
                }

            } catch (Exception e) {
                logger.error("Failed to optimize batch {}: {}", i + 1, e.getMessage());
                allOptimizedIds.addAll(batch.stream().map(Customer::getMyId).collect(Collectors.toList()));
            }
        }

        return new RouteResponse(
                allOptimizedIds,
                String.format("%.3f km", totalDistance).replace(".", ","),
                combinedGeometry.isEmpty() ? null : combinedGeometry
        );
    }

    private List<List<Customer>> createSimpleBatches(List<Customer> customers, int maxBatchSize) {
        List<List<Customer>> batches = new ArrayList<>();

        for (int i = 0; i < customers.size(); i += maxBatchSize) {
            int endIndex = Math.min(i + maxBatchSize, customers.size());
            batches.add(new ArrayList<>(customers.subList(i, endIndex)));
        }

        return batches;
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