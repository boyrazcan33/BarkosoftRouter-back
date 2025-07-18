package com.barkosoft.router.service;

import com.barkosoft.router.dto.OSRMResponse;
import com.barkosoft.router.dto.RouteResponse;
import com.barkosoft.router.model.Customer;
import com.barkosoft.router.repository.CustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

@Service
public class RouteService {

    private static final Logger logger = LoggerFactory.getLogger(RouteService.class);

    @Autowired
    private CustomerRepository customerRepository;

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

    public RouteResponse optimizeRoute(Double startLat, Double startLng, List<Long> customerIds) {
        List<Customer> customers = customerRepository.findAllById(customerIds);

        if (customers.isEmpty()) {
            logger.warn("No customers found for IDs: {}", customerIds);
            return new RouteResponse(new ArrayList<>(), "0,000 km");
        }

        // Build coordinates string for OSRM trip endpoint
        StringBuilder coordinates = new StringBuilder();
        coordinates.append(String.format("%f,%f", startLng, startLat)); // Start point

        for (Customer customer : customers) {
            coordinates.append(";").append(String.format("%f,%f", customer.getLongitude(), customer.getLatitude()));
        }

        try {
            String url = String.format("%s/trip/v1/driving/%s?source=first&roundtrip=false",
                    osrmBaseUrl, coordinates.toString());

            logger.debug("OSRM trip request: {}", url);

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            return parseOptimizedRouteFromResponse(response, customers);

        } catch (Exception e) {
            logger.error("OSRM trip API call failed: {}", e.getMessage());
            throw new RuntimeException("Route optimization failed: OSRM trip API error - " + e.getMessage());
        }
    }

    private RouteResponse parseOptimizedRouteFromResponse(String jsonResponse, List<Customer> customers) {
        try {
            logger.debug("OSRM trip response: {}", jsonResponse);

            OSRMResponse response = objectMapper.readValue(jsonResponse, OSRMResponse.class);

            if (!"Ok".equals(response.getCode())) {
                logger.error("OSRM returned error code: {} with message: {}",
                        response.getCode(), response.getMessage());
                throw new RuntimeException("OSRM trip optimization failed: " + response.getCode());
            }

            if (response.getWaypoints() == null || response.getWaypoints().isEmpty()) {
                logger.error("OSRM returned no waypoints");
                throw new RuntimeException("OSRM trip optimization failed: No waypoints returned");
            }

            if (response.getTrips() == null || response.getTrips().isEmpty()) {
                logger.error("OSRM returned no trips");
                throw new RuntimeException("OSRM trip optimization failed: No trips returned");
            }

            // Extract total distance from first trip
            Map<String, Object> trip = response.getTrips().get(0);
            Double totalDistanceMeters = ((Number) trip.get("distance")).doubleValue();
            Double totalDistanceKm = totalDistanceMeters / 1000.0; // Convert to kilometers

            // Skip first waypoint (start point) and map to customer IDs
            List<Long> optimizedRoute = new ArrayList<>();

            for (int i = 1; i < response.getWaypoints().size(); i++) {
                Map<String, Object> waypoint = response.getWaypoints().get(i);
                Integer waypointIndex = (Integer) waypoint.get("waypoint_index");

                if (waypointIndex != null) {
                    int customerIndex = waypointIndex - 1; // Adjust for start point

                    if (customerIndex >= 0 && customerIndex < customers.size()) {
                        optimizedRoute.add(customers.get(customerIndex).getMyId());
                    }
                }
            }

            logger.info("OSRM trip optimization completed successfully. Route order: {}, Total distance: {} km",
                    optimizedRoute, totalDistanceKm);

            return new RouteResponse(optimizedRoute, String.format("%.3f km", totalDistanceKm).replace(".", ","));

        } catch (Exception e) {
            logger.error("Failed to parse OSRM trip response: {}", e.getMessage());
            logger.debug("Raw OSRM response: {}", jsonResponse);
            throw new RuntimeException("Route optimization failed: Unable to parse OSRM response - " + e.getMessage());
        }
    }
}