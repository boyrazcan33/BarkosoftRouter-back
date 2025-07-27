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
}