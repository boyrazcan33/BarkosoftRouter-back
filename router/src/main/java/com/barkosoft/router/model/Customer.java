package com.barkosoft.router.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "customers")
@Data
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long myId;

    private Long companyId;

    private String customerRef;

    private String deliveryAddressRef;

    @NotNull
    private Double latitude;

    @NotNull
    private Double longitude;

    private String guId;
}