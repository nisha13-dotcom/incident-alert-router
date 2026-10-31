
package com.incidentrouter;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    public Incident createIncident(@Valid @RequestBody Incident incident) {
        return incidentService.createIncident(incident);
    }

    @GetMapping
    public List<Incident> getAllIncidents() {
        return incidentService.getAllIncidents();
    }

    @GetMapping("/filter")
    public List<Incident> filterIncidents(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String service) {

        if (severity != null && !severity.isBlank()) {
            return incidentService.getIncidentsBySeverity(severity);
        }

        if (service != null && !service.isBlank()) {
            return incidentService.getIncidentsByService(service);
        }

        return incidentService.getAllIncidents();
    }

    @PutMapping("/{id}/acknowledge")
    public Incident acknowledgeIncident(@PathVariable String id) {
        return incidentService.acknowledgeIncident(id);
    }

    @PostMapping("/{id}/retry-delivery")
    public Incident retryIncidentDelivery(@PathVariable String id) {
        return incidentService.retryIncidentDelivery(id);
    }
}

