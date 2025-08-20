package com.barkosoft.router.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchResult {
    private String jobId;
    private int batchIndex;
    private List<Long> optimizedCustomerIds;
    private double distanceKm;
    private List<List<Double>> routeGeometry; // Added geometry field
    private boolean success;
    private String errorMessage;

    // Constructor for successful results with geometry
    public BatchResult(String jobId, int batchIndex, List<Long> optimizedCustomerIds,
                       double distanceKm, List<List<Double>> routeGeometry) {
        this.jobId = jobId;
        this.batchIndex = batchIndex;
        this.optimizedCustomerIds = optimizedCustomerIds;
        this.distanceKm = distanceKm;
        this.routeGeometry = routeGeometry;
        this.success = true;
    }

    // Keep backward compatibility constructor without geometry
    public BatchResult(String jobId, int batchIndex, List<Long> optimizedCustomerIds, double distanceKm) {
        this(jobId, batchIndex, optimizedCustomerIds, distanceKm, null);
    }
}