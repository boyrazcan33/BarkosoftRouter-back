package com.barkosoft.router.service;

import com.barkosoft.router.model.Customer;
import com.barkosoft.router.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.*;

@Service
public class RouteService {

    @Autowired
    private CustomerRepository customerRepository;

    @Value("${osrm.base.url:http://router.project-osrm.org}")
    private String osrmBaseUrl;

    private final WebClient webClient;

    public RouteService() {
        this.webClient = WebClient.builder().build();
    }

    public List<Long> optimizeRoute(Double startLat, Double startLng, List<Long> customerIds) {
        List<Customer> customers = customerRepository.findAllById(customerIds);

        List<Long> result = new ArrayList<>();
        List<Customer> remaining = new ArrayList<>(customers);

        double currentLat = startLat;
        double currentLng = startLng;

        while (!remaining.isEmpty()) {
            Customer nearest = findNearestCustomer(currentLat, currentLng, remaining);
            result.add(nearest.getMyId());
            currentLat = nearest.getLatitude();
            currentLng = nearest.getLongitude();
            remaining.remove(nearest);
        }

        return result;
    }

    private Customer findNearestCustomer(double fromLat, double fromLng, List<Customer> customers) {
        Customer nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Customer customer : customers) {
            double distance = getDistanceFromOSRM(fromLat, fromLng, customer.getLatitude(), customer.getLongitude());
            if (distance < minDistance) {
                minDistance = distance;
                nearest = customer;
            }
        }

        return nearest;
    }

    private double getDistanceFromOSRM(double lat1, double lng1, double lat2, double lng2) {
        try {
            String url = osrmBaseUrl + "/route/v1/driving/" + lng1 + "," + lat1 + ";" + lng2 + "," + lat2 + "?overview=false";

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseDistanceFromResponse(response);
        } catch (Exception e) {
            return Double.MAX_VALUE;
        }
    }

    private double parseDistanceFromResponse(String response) {
        try {
            int distanceIndex = response.indexOf("\"distance\":");
            if (distanceIndex > 0) {
                String distanceStr = response.substring(distanceIndex + 11);
                int commaIndex = distanceStr.indexOf(",");
                if (commaIndex > 0) {
                    distanceStr = distanceStr.substring(0, commaIndex);
                }
                return Double.parseDouble(distanceStr);
            }
        } catch (Exception e) {}
        return Double.MAX_VALUE;
    }
}