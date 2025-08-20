package com.barkosoft.router.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse {

    private List<Long> optimizedCustomerIds;
    private String totalDistance;
    private String status;
    private List<List<Double>> routeGeometry; // Added geometry field

    // Constructor with geometry
    public RouteResponse(List<Long> optimizedCustomerIds, String totalDistance, List<List<Double>> routeGeometry) {
        this.optimizedCustomerIds = optimizedCustomerIds;
        this.totalDistance = totalDistance;
        this.status = "success";
        this.routeGeometry = routeGeometry;
    }

    // Keep backward compatibility constructor without geometry
    public RouteResponse(List<Long> optimizedCustomerIds, String totalDistance) {
        this(optimizedCustomerIds, totalDistance, null);
    }
}