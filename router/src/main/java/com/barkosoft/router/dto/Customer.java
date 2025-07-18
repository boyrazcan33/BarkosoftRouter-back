package com.barkosoft.router.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Customer {

    @NotNull
    private Long myId;

    @NotNull
    private Double latitude;

    @NotNull
    private Double longitude;
}