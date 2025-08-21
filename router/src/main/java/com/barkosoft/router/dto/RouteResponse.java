package com.barkosoft.router.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse {

    private List<Long> optimizedCustomerIds;
    private String totalDistance;
    private String status;
    private List<List<Double>> routeGeometry;
    private Map<Long, int[]> customerGeometryMapping;

    public RouteResponse(List<Long> optimizedCustomerIds, String totalDistance,
                         List<List<Double>> routeGeometry, Map<Long, int[]> customerGeometryMapping) {
        this.optimizedCustomerIds = optimizedCustomerIds;
        this.totalDistance = totalDistance;
        this.status = "success";
        this.routeGeometry = routeGeometry;
        this.customerGeometryMapping = customerGeometryMapping;
    }

    public RouteResponse(List<Long> optimizedCustomerIds, String totalDistance, List<List<Double>> routeGeometry) {
        this(optimizedCustomerIds, totalDistance, routeGeometry, null);
    }

    public RouteResponse(List<Long> optimizedCustomerIds, String totalDistance) {
        this(optimizedCustomerIds, totalDistance, null, null);
    }
}