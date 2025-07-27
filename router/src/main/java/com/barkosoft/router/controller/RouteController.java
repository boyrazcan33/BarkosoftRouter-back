package com.barkosoft.router.controller;

import com.barkosoft.router.dto.RouteRequest;
import com.barkosoft.router.dto.RouteResponse;
import com.barkosoft.router.service.KafkaRouteProducer;
import com.barkosoft.router.service.JobTrackingService;
import com.barkosoft.router.service.RouteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/route")
public class RouteController {

    private static final Logger logger = LoggerFactory.getLogger(RouteController.class);

    @Autowired
    private RouteService routeService;

    @Autowired
    private KafkaRouteProducer kafkaRouteProducer;

    @Autowired
    private JobTrackingService jobTrackingService;

    @Value("${kafka.enabled:true}")
    private boolean kafkaEnabled;

    @Value("${kafka.batch.threshold:50}")
    private int kafkaBatchThreshold;

    @PostMapping("/optimize")
    public ResponseEntity<RouteResponse> optimizeRoute(@Valid @RequestBody RouteRequest request) {
        try {
            int customerCount = request.getCustomers().size();
            logger.info("Received optimization request for {} customers", customerCount);

            // Use Kafka for large datasets, direct processing for small ones
            if (kafkaEnabled && customerCount > kafkaBatchThreshold) {
                return handleWithKafka(request);
            } else {
                return handleDirectly(request);
            }

        } catch (Exception e) {
            logger.error("Route optimization failed: {}", e.getMessage());
            RouteResponse errorResponse = new RouteResponse();
            errorResponse.setOptimizedCustomerIds(List.of());
            errorResponse.setTotalDistance("0,000 km");
            errorResponse.setStatus("error");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    private ResponseEntity<RouteResponse> handleWithKafka(RouteRequest request) {
        String jobId = kafkaRouteProducer.submitOptimizationJob(
                request.getStartLatitude(),
                request.getStartLongitude(),
                request.getCustomers()
        );

        // Wait for results with 3-minute timeout
        RouteResponse response = jobTrackingService.waitForResult(jobId, Duration.ofMinutes(3));

        if (response.getStatus() != null && response.getStatus().startsWith("error")) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(response);
    }

    private ResponseEntity<RouteResponse> handleDirectly(RouteRequest request) {
        RouteResponse response = routeService.optimizeRoute(
                request.getStartLatitude(),
                request.getStartLongitude(),
                request.getCustomers()
        );
        return ResponseEntity.ok(response);
    }
}