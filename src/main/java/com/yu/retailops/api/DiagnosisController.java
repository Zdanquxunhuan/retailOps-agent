package com.yu.retailops.api;

import com.yu.retailops.domain.DiagnosisReport;
import com.yu.retailops.workflow.DiagnosisStore;
import com.yu.retailops.workflow.DiagnosisWorkflow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping(value="/api/v1/diagnoses", produces=MediaType.APPLICATION_JSON_VALUE)
public class DiagnosisController {
    private final DiagnosisWorkflow workflow;
    public DiagnosisController(DiagnosisWorkflow workflow) { this.workflow = workflow; }

    @PostMapping(value="/meituan-sales", consumes=MediaType.APPLICATION_JSON_VALUE)
    public DiagnosisReport diagnose(@Valid @RequestBody Request request, Principal principal) {
        return workflow.diagnose(principal.getName(), request.message());
    }

    @GetMapping("/{taskId}")
    public DiagnosisStore.StoredTask get(@PathVariable UUID taskId, Principal principal) {
        return workflow.get(taskId, principal.getName());
    }

    public record Request(@NotBlank @Size(max=2000) String message) { }
}
