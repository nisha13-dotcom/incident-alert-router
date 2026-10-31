
package com.incidentrouter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class IncidentEscalationService {

    private final IncidentRepository incidentRepository;

    public IncidentEscalationService(
            IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    public int escalateOverdueIncidents() {

        // Escalate incidents that are at least 5 minutes old
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);

        List<Incident> incidents =
                incidentRepository.findIncidentsForEscalation(cutoff);

        int escalatedCount = 0;

        for (Incident incident : incidents) {

            // Count only successfully escalated incidents
            if (incidentRepository.escalateIncident(incident, cutoff).isPresent()) {

                escalatedCount++;

                System.out.println(
                        "Incident escalated: " + incident.getId()
                );
            }
        }

        System.out.println(
                "Total incidents escalated: " + escalatedCount
        );

        return escalatedCount;
    }
}
