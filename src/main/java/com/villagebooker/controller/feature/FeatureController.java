package com.villagebooker.controller.feature;

import com.villagebooker.dto.feature.FeatureRequest;
import com.villagebooker.dto.feature.FeatureResponse;
import com.villagebooker.service.feature.FeatureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Feature catalog: everyone reads it (public, for the search filters), only the admin changes it
@RestController
@RequiredArgsConstructor
public class FeatureController {

    private final FeatureService featureService;

    @GetMapping("/api/features")
    public List<FeatureResponse> findAll() {
        return featureService.findAll();
    }

    @PostMapping("/api/admin/features")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public FeatureResponse create(@Valid @RequestBody FeatureRequest request) {
        return featureService.create(request);
    }

    @PutMapping("/api/admin/features/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public FeatureResponse update(@PathVariable Long id, @Valid @RequestBody FeatureRequest request) {
        return featureService.update(id, request);
    }

    @DeleteMapping("/api/admin/features/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        featureService.delete(id);
    }
}
