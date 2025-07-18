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

@Service
public class RouteService {

    private static final Logger logger = LoggerFactory.getLogger(RouteService.class);

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
                    .timeout(Duration.ofSeconds(30))
                    .block();

            return parseOptimizedRouteFromResponse(response, customers);

        } catch (Exception e) {
            logger.error("OSRM API call failed: {}", e.getMessage());
            throw new RuntimeException("Route optimization failed: " + e.getMessage());
        }
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