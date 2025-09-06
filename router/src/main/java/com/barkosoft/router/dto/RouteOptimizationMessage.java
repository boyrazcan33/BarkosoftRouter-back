package com.barkosoft.router.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteOptimizationMessage {
    private String jobId;
    private Double startLatitude;
    private Double startLongitude;
    private List<Customer> batch;
    private int batchIndex;
    private int totalBatches;

    // Previous batch's last customer coordinates for continuity
    private Double previousBatchLastLat;
    private Double previousBatchLastLng;

    // Constructor for backward compatibility
    public RouteOptimizationMessage(String jobId, Double startLatitude, Double startLongitude,
                                    List<Customer> batch, int batchIndex, int totalBatches) {
        this.jobId = jobId;
        this.startLatitude = startLatitude;
        this.startLongitude = startLongitude;
        this.batch = batch;
        this.batchIndex = batchIndex;
        this.totalBatches = totalBatches;
        this.previousBatchLastLat = null;
        this.previousBatchLastLng = null;
    }
}