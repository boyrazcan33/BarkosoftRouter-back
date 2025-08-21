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
    private static final int BATCH_SIZE = 50;

    @Value("${osrm.base.url:http://router.project-osrm.org}")
    private String osrmBaseUrl;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public RouteService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB limit
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public RouteResponse optimizeRoute(Double startLat, Double startLng, List<Customer> customers) {
        if (customers.isEmpty()) {
            logger.warn("No customers provided in request");
            return new RouteResponse(new ArrayList<>(), "0,000 km", null, null);
        }

        if (customers.size() <= BATCH_SIZE) {
            return optimizeSingleBatch(startLat, startLng, customers);
        }

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
            RouteGeometryResult geometryResult = fetchRouteGeometryWithMapping(
                    startLat, startLng, customers, optimizedRoute.getOptimizedCustomerIds()
            );

            return new RouteResponse(
                    optimizedRoute.getOptimizedCustomerIds(),
                    optimizedRoute.getTotalDistance(),
                    geometryResult != null ? geometryResult.geometry : null,
                    geometryResult != null ? geometryResult.customerMapping : null
            );

        } catch (Exception e) {
            logger.error("OSRM API call failed for {} customers: {}", customers.size(), e.getMessage());
            throw new RuntimeException("Route optimization failed: " + e.getMessage());
        }
    }

    private static class RouteGeometryResult {
        List<List<Double>> geometry;
        Map<Long, int[]> customerMapping;

        RouteGeometryResult(List<List<Double>> geometry, Map<Long, int[]> customerMapping) {
            this.geometry = geometry;
            this.customerMapping = customerMapping;
        }
    }

    @SuppressWarnings("unchecked")
    private RouteGeometryResult fetchRouteGeometryWithMapping(Double startLat, Double startLng,
                                                              List<Customer> customers,
                                                              List<Long> optimizedCustomerIds) {
        try {
            Map<Long, Customer> customerMap = customers.stream()
                    .collect(Collectors.toMap(Customer::getMyId, c -> c));

            StringBuilder orderedCoordinates = new StringBuilder();
            orderedCoordinates.append(String.format("%f,%f", startLng, startLat));

            for (Long customerId : optimizedCustomerIds) {
                Customer customer = customerMap.get(customerId);
                if (customer != null) {
                    orderedCoordinates.append(";")
                            .append(String.format("%f,%f", customer.getLongitude(), customer.getLatitude()));
                }
            }

            String routeUrl = String.format("%s/route/v1/driving/%s?geometries=geojson&overview=full&annotations=true",
                    osrmBaseUrl, orderedCoordinates.toString());

            logger.info("Fetching route geometry with mapping for {} customers", optimizedCustomerIds.size());

            String routeResponse = webClient.get()
                    .uri(routeUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            Map<String, Object> response = objectMapper.readValue(routeResponse, Map.class);
            if (!"Ok".equals(response.get("code"))) {
                logger.warn("OSRM returned non-OK status: {}", response.get("code"));
                return null;
            }

            List<Map<String, Object>> routes = (List<Map<String, Object>>) response.get("routes");
            if (routes == null || routes.isEmpty()) {
                logger.warn("No routes found in response");
                return null;
            }

            Map<String, Object> route = routes.get(0);

            // Get geometry
            Map<String, Object> geometry = (Map<String, Object>) route.get("geometry");
            List<List<Double>> coordinates = (List<List<Double>>) geometry.get("coordinates");

            // Parse legs to create mapping
            Map<Long, int[]> customerMapping = new HashMap<>();
            List<Map<String, Object>> legs = (List<Map<String, Object>>) route.get("legs");

            logger.info("Route has {} legs for {} customers", legs != null ? legs.size() : 0, optimizedCustomerIds.size());

            if (legs != null && !legs.isEmpty()) {
                int currentIndex = 0;

                // Each leg represents a segment from one waypoint to the next
                for (int i = 0; i < legs.size() && i < optimizedCustomerIds.size(); i++) {
                    Map<String, Object> leg = legs.get(i);

                    // Get the number of geometry points in this leg
                    int legPointCount = 0;
                    Map<String, Object> annotation = (Map<String, Object>) leg.get("annotation");

                    if (annotation != null) {
                        List<?> distances = (List<?>) annotation.get("distance");
                        if (distances != null) {
                            legPointCount = distances.size();
                            logger.debug("Leg {} has {} points from annotation", i, legPointCount);
                        }
                    }

                    // If no annotation, estimate based on distance
                    if (legPointCount == 0) {
                        Double distance = ((Number) leg.get("distance")).doubleValue();
                        // Rough estimate: 1 point per 50 meters
                        legPointCount = Math.max(1, (int)(distance / 50));
                        logger.debug("Leg {} estimated {} points based on distance {}", i, legPointCount, distance);
                    }

                    Long customerId = optimizedCustomerIds.get(i);
                    customerMapping.put(customerId, new int[]{currentIndex, currentIndex + legPointCount});
                    logger.debug("Customer {} -> geometry points [{}, {}]", customerId, currentIndex, currentIndex + legPointCount);

                    currentIndex += legPointCount;
                }
            } else {
                logger.warn("No legs found in route response, creating simple mapping");
                // Fallback: divide geometry equally among customers
                if (!optimizedCustomerIds.isEmpty() && coordinates != null) {
                    int pointsPerCustomer = coordinates.size() / optimizedCustomerIds.size();
                    for (int i = 0; i < optimizedCustomerIds.size(); i++) {
                        int start = i * pointsPerCustomer;
                        int end = (i == optimizedCustomerIds.size() - 1) ? coordinates.size() : (i + 1) * pointsPerCustomer;
                        customerMapping.put(optimizedCustomerIds.get(i), new int[]{start, end});
                    }
                }
            }

            logger.info("Created geometry mapping for {} customers", customerMapping.size());
            return new RouteGeometryResult(coordinates, customerMapping.isEmpty() ? null : customerMapping);

        } catch (Exception e) {
            logger.error("Failed to fetch route geometry with mapping: {}", e.getMessage(), e);
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

                if (batchResponse.getRouteGeometry() != null) {
                    if (i == 0) {
                        combinedGeometry.addAll(batchResponse.getRouteGeometry());
                    } else if (batchResponse.getRouteGeometry().size() > 1) {
                        combinedGeometry.addAll(batchResponse.getRouteGeometry().subList(1, batchResponse.getRouteGeometry().size()));
                    }
                }

                String distanceStr = batchResponse.getTotalDistance().replace(" km", "").replace(",", ".");
                totalDistance += Double.parseDouble(distanceStr);

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
                combinedGeometry.isEmpty() ? null : combinedGeometry,
                null
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