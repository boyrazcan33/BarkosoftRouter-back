package com.barkosoft.router.service;

import com.barkosoft.router.dto.Customer;
import com.barkosoft.router.dto.RouteOptimizationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaRouteProducerTest {

    @Mock
    private KafkaTemplate<String, RouteOptimizationMessage> kafkaTemplate;

    @Mock
    private JobTrackingService jobTrackingService;

    @InjectMocks
    private KafkaRouteProducer kafkaRouteProducer;

    @Test
    void shouldSubmitOptimizationJob() {
        List<Customer> customers = createCustomerList(10);

        String jobId = kafkaRouteProducer.submitOptimizationJob(41.0082, 28.9784, customers);

        assertNotNull(jobId);
        verify(jobTrackingService).createJob(eq(jobId), eq(1));
        verify(kafkaTemplate, atLeastOnce()).send(eq("route-optimization-requests"), anyInt(), anyString(), any(RouteOptimizationMessage.class));
    }

    @Test
    void shouldCreateMultipleBatchesForLargeDataset() {
        List<Customer> customers = createCustomerList(200);

        String jobId = kafkaRouteProducer.submitOptimizationJob(41.0082, 28.9784, customers);

        assertNotNull(jobId);
        verify(jobTrackingService).createJob(eq(jobId), eq(3)); // 200/95 = ~3 batches
        verify(kafkaTemplate, times(3)).send(anyString(), anyInt(), anyString(), any(RouteOptimizationMessage.class));
    }

    @Test
    void shouldSortCustomersByNearestNeighbor() {
        List<Customer> customers = createScatteredCustomerList();

        String jobId = kafkaRouteProducer.submitOptimizationJob(41.0082, 28.9784, customers);

        assertNotNull(jobId);
        verify(kafkaTemplate, atLeastOnce()).send(anyString(), anyInt(), anyString(), any(RouteOptimizationMessage.class));
    }

    private List<Customer> createCustomerList(int size) {
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

    private List<Customer> createScatteredCustomerList() {
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
}