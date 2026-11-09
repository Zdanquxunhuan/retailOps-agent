package com.yu.retailops.api;

import com.yu.retailops.agent.AgentDiagnosisService;
import com.yu.retailops.domain.AgentDiagnosisRun;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping(value="/api/v1/diagnoses/agent", produces=MediaType.APPLICATION_JSON_VALUE)
public class AgentDiagnosisController {
    private final AgentDiagnosisService service;
    public AgentDiagnosisController(AgentDiagnosisService service) { this.service = service; }
    @PostMapping(value="/meituan-sales", consumes=MediaType.APPLICATION_JSON_VALUE)
    public AgentDiagnosisRun diagnose(@Valid @RequestBody Request request, Principal principal) {
        return service.diagnose(principal.getName(), request.message(), request.orderDate());
    }
    @GetMapping("/{taskId}")
    public AgentDiagnosisRun get(@PathVariable UUID taskId, Principal principal) {
        return service.get(taskId, principal.getName());
    }
    public record Request(@NotBlank @Size(max=2000) String message, LocalDate orderDate) { }
}
