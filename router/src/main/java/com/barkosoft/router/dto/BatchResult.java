package com.barkosoft.router.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchResult {
    private String jobId;
    private int batchIndex;
    private List<Long> optimizedCustomerIds;
    private double distanceKm;
    private List<List<Double>> routeGeometry;
    private Map<Long, int[]> customerGeometryMapping; // Added field
    private boolean success;
    private String errorMessage;

    // Constructor for successful results with geometry and mapping
    public BatchResult(String jobId, int batchIndex, List<Long> optimizedCustomerIds,
                       double distanceKm, List<List<Double>> routeGeometry,
                       Map<Long, int[]> customerGeometryMapping) {
        this.jobId = jobId;
        this.batchIndex = batchIndex;
        this.optimizedCustomerIds = optimizedCustomerIds;
        this.distanceKm = distanceKm;
        this.routeGeometry = routeGeometry;
        this.customerGeometryMapping = customerGeometryMapping;
        this.success = true;
    }
}