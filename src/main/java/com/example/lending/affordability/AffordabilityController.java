package com.example.lending.affordability;

import com.example.lending.affordability.dto.AffordabilityPreCheckRequest;
import com.example.lending.affordability.dto.AffordabilityPreCheckResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loan-applications")
public class AffordabilityController {

    private final AffordabilityService affordabilityService;

    public AffordabilityController(AffordabilityService affordabilityService) {
        this.affordabilityService = affordabilityService;
    }

    @PostMapping("/{applicationId}/affordability-pre-check")
    public AffordabilityPreCheckResponse preCheck(
            @PathVariable UUID applicationId,
            @Valid @RequestBody(required = false) AffordabilityPreCheckRequest request) {
        return affordabilityService.preCheck(applicationId, request);
    }
}
