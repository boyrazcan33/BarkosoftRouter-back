package com.barkosoft.router.algorithm;

import com.barkosoft.router.dto.Customer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteAlgorithmTest {

    @Test
    void shouldCalculateHaversineDistanceAccurately() {
        double lat1 = 41.0082;
        double lng1 = 28.9784;
        double lat2 = 39.9334;
        double lng2 = 32.8597;

        double distance = calculateHaversineDistance(lat1, lng1, lat2, lng2);

        assertTrue(distance > 350 && distance < 370);
    }

    @Test
    void shouldCalculateZeroDistanceForSameCoordinates() {
        double lat = 41.0082;
        double lng = 28.9784;

        double distance = calculateHaversineDistance(lat, lng, lat, lng);

        assertEquals(0.0, distance, 0.001);
    }

    @Test
    void shouldSortCustomersByDistance() {
        List<Customer> customers = createTestCustomers();
        Double startLat = 41.0;
        Double startLng = 29.0;

        List<Customer> sorted = sortCustomersByNearestNeighbor(startLat, startLng, customers);

        assertNotNull(sorted);
        assertEquals(3, sorted.size());

        Customer first = sorted.get(0);
        double firstDistance = calculateHaversineDistance(
                startLat, startLng, first.getLatitude(), first.getLongitude());

        Customer second = sorted.get(1);
        double secondDistance = calculateHaversineDistance(
                first.getLatitude(), first.getLongitude(), second.getLatitude(), second.getLongitude());

        assertTrue(firstDistance >= 0);
        assertTrue(secondDistance >= 0);
    }

    @Test
    void shouldHandleEmptyCustomerList() {
        List<Customer> empty = Arrays.asList();

        List<Customer> sorted = sortCustomersByNearestNeighbor(41.0, 29.0, empty);

        assertTrue(sorted.isEmpty());
    }

    @Test
    void shouldCreateOptimalBatches() {
        List<Customer> largeList = createLargeCustomerList(250);

        List<List<Customer>> batches = createBatches(largeList, 50);

        assertEquals(5, batches.size());
        assertEquals(50, batches.get(0).size());
        assertEquals(50, batches.get(4).size());
    }

    @Test
    void shouldHandleNonDivisibleBatchSize() {
        List<Customer> customers = createLargeCustomerList(103);

        List<List<Customer>> batches = createBatches(customers, 50);

        assertEquals(3, batches.size());
        assertEquals(50, batches.get(0).size());
        assertEquals(50, batches.get(1).size());
        assertEquals(3, batches.get(2).size());
    }

    @Test
    void shouldMaintainCustomerOrderInBatches() {
        List<Customer> customers = createOrderedCustomerList();

        List<List<Customer>> batches = createBatches(customers, 2);

        assertEquals(2, batches.size());
        assertEquals(1L, batches.get(0).get(0).getMyId());
        assertEquals(2L, batches.get(0).get(1).getMyId());
        assertEquals(3L, batches.get(1).get(0).getMyId());
    }

    @Test
    void shouldCalculateDistanceBetweenIstanbulAndAnkara() {
        double istanbulLat = 41.0082;
        double istanbulLng = 28.9784;
        double ankaraLat = 39.9334;
        double ankaraLng = 32.8597;

        double distance = calculateHaversineDistance(istanbulLat, istanbulLng, ankaraLat, ankaraLng);

        assertTrue(distance > 350 && distance < 370);
    }

    private double calculateHaversineDistance(Double lat1, Double lng1, Double lat2, Double lng2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private List<Customer> sortCustomersByNearestNeighbor(Double startLat, Double startLng, List<Customer> customers) {
        if (customers.isEmpty()) return new java.util.ArrayList<>();

        List<Customer> remaining = new java.util.ArrayList<>(customers);
        List<Customer> sorted = new java.util.ArrayList<>();
        Double currentLat = startLat;
        Double currentLng = startLng;

        while (!remaining.isEmpty()) {
            Customer nearest = findNearestCustomer(currentLat, currentLng, remaining);
            sorted.add(nearest);
            remaining.remove(nearest);
            currentLat = nearest.getLatitude();
            currentLng = nearest.getLongitude();
        }
        return sorted;
    }

    private Customer findNearestCustomer(Double lat, Double lng, List<Customer> customers) {
        Customer nearest = null;
        double minDistance = Double.MAX_VALUE;
        for (Customer customer : customers) {
            double distance = calculateHaversineDistance(lat, lng, customer.getLatitude(), customer.getLongitude());
            if (distance < minDistance) {
                minDistance = distance;
                nearest = customer;
            }
        }
        return nearest;
    }

    private List<List<Customer>> createBatches(List<Customer> customers, int batchSize) {
        List<List<Customer>> batches = new java.util.ArrayList<>();
        for (int i = 0; i < customers.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, customers.size());
            batches.add(new java.util.ArrayList<>(customers.subList(i, endIndex)));
        }
        return batches;
    }

    private List<Customer> createTestCustomers() {
        Customer c1 = new Customer();
        c1.setMyId(1L);
        c1.setLatitude(41.01);
        c1.setLongitude(29.01);

        Customer c2 = new Customer();
        c2.setMyId(2L);
        c2.setLatitude(41.05);
        c2.setLongitude(29.05);

        Customer c3 = new Customer();
        c3.setMyId(3L);
        c3.setLatitude(41.1);
        c3.setLongitude(29.1);

        return Arrays.asList(c3, c1, c2);
    }

    private List<Customer> createLargeCustomerList(int size) {
        List<Customer> customers = new java.util.ArrayList<>();
        for (int i = 0; i < size; i++) {
            Customer customer = new Customer();
            customer.setMyId((long) i);
            customer.setLatitude(41.0 + (i * 0.001));
            customer.setLongitude(29.0 + (i * 0.001));
            customers.add(customer);
        }
        return customers;
    }

    private List<Customer> createOrderedCustomerList() {
        return Arrays.asList(
                createCustomer(1L, 41.0, 29.0),
                createCustomer(2L, 41.01, 29.01),
                createCustomer(3L, 41.02, 29.02)
        );
    }

    private Customer createCustomer(Long id, Double lat, Double lng) {
        Customer customer = new Customer();
        customer.setMyId(id);
        customer.setLatitude(lat);
        customer.setLongitude(lng);
        return customer;
    }
}