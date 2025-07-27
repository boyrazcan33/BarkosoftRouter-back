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
    private boolean success;
    private String errorMessage;

    public BatchResult(String jobId, int batchIndex, List<Long> optimizedCustomerIds, double distanceKm) {
        this.jobId = jobId;
        this.batchIndex = batchIndex;
        this.optimizedCustomerIds = optimizedCustomerIds;
        this.distanceKm = distanceKm;
        this.success = true;
    }
}