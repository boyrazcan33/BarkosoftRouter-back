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
    private Double totalDistance;
    private String status;

    public RouteResponse(List<Long> optimizedCustomerIds, Double totalDistance) {
        this.optimizedCustomerIds = optimizedCustomerIds;
        this.totalDistance = totalDistance;
        this.status = "success";
    }
}