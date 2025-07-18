package com.barkosoft.router.dto;

import com.barkosoft.router.dto.Customer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
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
    @Valid
    private List<Customer> customers;
}