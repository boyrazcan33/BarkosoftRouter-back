package com.barkosoft.router.service;

import com.barkosoft.router.dto.Customer;
import com.barkosoft.router.dto.RouteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private RouteService routeService;

    private List<Customer> customers;

    @BeforeEach
    void setUp() {
        Customer customer1 = new Customer();
        customer1.setMyId(1L);
        customer1.setLatitude(41.0180);
        customer1.setLongitude(28.9647);

        Customer customer2 = new Customer();
        customer2.setMyId(2L);
        customer2.setLatitude(41.0082);
        customer2.setLongitude(28.9784);

        customers = Arrays.asList(customer1, customer2);
    }

    @Test
    void shouldOptimizeSmallBatch() {
        String mockResponse = "{\"code\":\"Ok\",\"trips\":[{\"distance\":5000}],\"waypoints\":[{\"waypoint_index\":0},{\"waypoint_index\":1},{\"waypoint_index\":2}]}";

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(mockResponse));

        RouteResponse result = routeService.optimizeSingleBatch(41.0082, 28.9784, customers);

        assertNotNull(result);
        assertEquals(2, result.getOptimizedCustomerIds().size());
        assertTrue(result.getTotalDistance().contains("km"));
    }

    @Test
    void shouldHandleEmptyCustomerList() {
        RouteResponse result = routeService.optimizeRoute(41.0082, 28.9784, Arrays.asList());

        assertNotNull(result);
        assertTrue(result.getOptimizedCustomerIds().isEmpty());
        assertEquals("0,000 km", result.getTotalDistance());
    }

    @Test
    void shouldUseBatchingForLargeDataset() {
        List<Customer> largeCustomerList = createLargeCustomerList(100);

        RouteResponse result = routeService.optimizeRoute(41.0082, 28.9784, largeCustomerList);

        assertNotNull(result);
        assertEquals(100, result.getOptimizedCustomerIds().size());
        assertTrue(result.getTotalDistance().contains("km"));
    }

    @Test
    void shouldHandleOSRMAPIFailure() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("API Error")));

        assertThrows(RuntimeException.class, () -> {
            routeService.optimizeSingleBatch(41.0082, 28.9784, customers);
        });
    }

    @Test
    void shouldOptimizeWithSortedCustomers() {
        List<Customer> scatteredCustomers = createScatteredCustomers();

        RouteResponse result = routeService.optimizeRoute(41.0082, 28.9784, scatteredCustomers);

        assertNotNull(result);
        assertEquals(3, result.getOptimizedCustomerIds().size());
        assertFalse(result.getTotalDistance().equals("0,000 km"));
    }

    @Test
    void shouldReturnValidDistanceFormat() {
        List<Customer> testCustomers = createTestCustomers();

        RouteResponse result = routeService.optimizeRoute(41.0082, 28.9784, testCustomers);

        assertNotNull(result);
        assertTrue(result.getTotalDistance().matches("\\d+,\\d{3} km"));
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

    private List<Customer> createScatteredCustomers() {
        Customer c1 = new Customer();
        c1.setMyId(1L);
        c1.setLatitude(41.0);
        c1.setLongitude(29.0);

        Customer c2 = new Customer();
        c2.setMyId(2L);
        c2.setLatitude(41.1);
        c2.setLongitude(29.1);

        Customer c3 = new Customer();
        c3.setMyId(3L);
        c3.setLatitude(41.05);
        c3.setLongitude(29.05);

        return Arrays.asList(c1, c2, c3);
    }

    private List<Customer> createTestCustomers() {
        Customer c1 = new Customer();
        c1.setMyId(1L);
        c1.setLatitude(41.0180);
        c1.setLongitude(28.9647);

        Customer c2 = new Customer();
        c2.setMyId(2L);
        c2.setLatitude(41.0150);
        c2.setLongitude(28.9700);

        return Arrays.asList(c1, c2);
    }
}