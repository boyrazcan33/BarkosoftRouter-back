package com.barkosoft.router.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class OSRMResponse {
    private String code;
    private List<Map<String, Object>> trips;
    private List<Map<String, Object>> waypoints;
    private String message;
}