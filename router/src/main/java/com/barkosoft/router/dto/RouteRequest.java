package com.barkosoft.router.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class RouteRequest {

    @NotNull
    private Double startLatitude;

    @NotNull
    private Double startLongitude;

    @NotEmpty
    private List<Long> customerIds;
}