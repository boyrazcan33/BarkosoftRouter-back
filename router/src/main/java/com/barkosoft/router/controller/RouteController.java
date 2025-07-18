package com.barkosoft.router.controller;

import com.barkosoft.router.dto.RouteRequest;
import com.barkosoft.router.dto.RouteResponse;
import com.barkosoft.router.service.RouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/route")
public class RouteController {

    @Autowired
    private RouteService routeService;

    @PostMapping("/optimize")
    public ResponseEntity<RouteResponse> optimizeRoute(@Valid @RequestBody RouteRequest request) {
        try {
            RouteResponse response = routeService.optimizeRoute(
                    request.getStartLatitude(),
                    request.getStartLongitude(),
                    request.getCustomerIds()
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            RouteResponse errorResponse = new RouteResponse();
            errorResponse.setStatus("error");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}