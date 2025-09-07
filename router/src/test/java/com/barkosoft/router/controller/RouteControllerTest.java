package com.barkosoft.router.controller;

import com.barkosoft.router.dto.Customer;
import com.barkosoft.router.dto.RouteRequest;
import com.barkosoft.router.dto.RouteResponse;
import com.barkosoft.router.service.RouteService;
import com.barkosoft.router.service.KafkaRouteProducer;
import com.barkosoft.router.service.JobTrackingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RouteControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RouteService routeService;

    @Mock
    private KafkaRouteProducer kafkaRouteProducer;

    @Mock
    private JobTrackingService jobTrackingService;

    @InjectMocks
    private RouteController routeController;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(routeController).build();
        // Set kafka properties using reflection
        org.springframework.test.util.ReflectionTestUtils.setField(routeController, "kafkaEnabled", false);
        org.springframework.test.util.ReflectionTestUtils.setField(routeController, "kafkaBatchThreshold", 50);
    }

    @Test
    void shouldOptimizeRouteWithValidRequest() throws Exception {
        RouteRequest request = createValidRouteRequest();
        RouteResponse response = createMockRouteResponse();

        when(routeService.optimizeRoute(anyDouble(), anyDouble(), any())).thenReturn(response);

        mockMvc.perform(post("/api/route/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optimizedCustomerIds").isArray())
                .andExpect(jsonPath("$.totalDistance").value("5,000 km"))
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void shouldReturnBadRequestForInvalidCoordinates() throws Exception {
        RouteRequest request = new RouteRequest();
        request.setStartLatitude(null);
        request.setStartLongitude(28.9784);
        request.setCustomers(Arrays.asList(createCustomer(1L, 41.0, 29.0)));

        mockMvc.perform(post("/api/route/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestForEmptyCustomers() throws Exception {
        RouteRequest request = new RouteRequest();
        request.setStartLatitude(41.0082);
        request.setStartLongitude(28.9784);
        request.setCustomers(Arrays.asList());

        mockMvc.perform(post("/api/route/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldHandleServiceException() throws Exception {
        RouteRequest request = createValidRouteRequest();

        when(routeService.optimizeRoute(anyDouble(), anyDouble(), any()))
                .thenThrow(new RuntimeException("OSRM API Error"));

        mockMvc.perform(post("/api/route/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void shouldValidateCustomerFields() throws Exception {
        RouteRequest request = new RouteRequest();
        request.setStartLatitude(41.0082);
        request.setStartLongitude(28.9784);

        Customer invalidCustomer = new Customer();
        invalidCustomer.setMyId(null);
        invalidCustomer.setLatitude(41.0);
        invalidCustomer.setLongitude(29.0);

        request.setCustomers(Arrays.asList(invalidCustomer));

        mockMvc.perform(post("/api/route/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private RouteRequest createValidRouteRequest() {
        RouteRequest request = new RouteRequest();
        request.setStartLatitude(41.0082);
        request.setStartLongitude(28.9784);
        request.setCustomers(Arrays.asList(
                createCustomer(1L, 41.0180, 28.9647),
                createCustomer(2L, 41.0150, 28.9700)
        ));
        return request;
    }

    private Customer createCustomer(Long id, Double lat, Double lng) {
        Customer customer = new Customer();
        customer.setMyId(id);
        customer.setLatitude(lat);
        customer.setLongitude(lng);
        return customer;
    }

    private RouteResponse createMockRouteResponse() {
        RouteResponse response = new RouteResponse();
        response.setOptimizedCustomerIds(Arrays.asList(1L, 2L));
        response.setTotalDistance("5,000 km");
        response.setStatus("success");
        return response;
    }
}