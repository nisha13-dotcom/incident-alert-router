
package com.incidentrouter;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentQueueService incidentQueueService;
    private final IncidentFingerprintRepository fingerprintRepository;
    private final IncidentCreationTransaction creationTransaction;

    public IncidentService(
            IncidentRepository incidentRepository,
            IncidentQueueService incidentQueueService,
            IncidentFingerprintRepository fingerprintRepository,
            IncidentCreationTransaction creationTransaction) {
        this.incidentRepository = incidentRepository;
        this.incidentQueueService = incidentQueueService;
        this.fingerprintRepository = fingerprintRepository;
        this.creationTransaction = creationTransaction;
    }

    public Incident createIncident(Incident incident) {
        incident.assignMetadata();

        String fingerprint = IncidentFingerprint.generate(incident);
        incident.setFingerprint(fingerprint);
        incident.setProcessingStatus("PENDING");

        // Atomically create the incident and claim its fingerprint.
        try {
            creationTransaction.createIncidentAndClaimFingerprint(incident);
        } catch (TransactionCanceledException exception) {

            // A cancellation can have causes other than a duplicate.
            // Check whether the fingerprint actually exists.
            if (!creationTransaction.isFingerprintAlreadyClaimed(fingerprint)) {
                throw exception;
            }

            String existingId = fingerprintRepository
                    .findIncidentId(fingerprint)
                    .orElseThrow(() -> exception);

            return incidentRepository.findById(existingId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Fingerprint index references a missing incident: "
                                    + existingId));
        }

        // The database transaction succeeded. Now send the SQS message.
        try {
            incidentQueueService.sendIncidentMessage(incident);
        } catch (RuntimeException exception) {
            incident.setProcessingStatus("DELIVERY_FAILED");

            try {
                incidentRepository.save(incident);
            } catch (RuntimeException statusException) {
                exception.addSuppressed(statusException);
            }

            throw new IllegalStateException(
                    "Incident was saved, but SQS delivery failed. Incident ID: "
                            + incident.getId(),
                    exception);
        }

        return incident;
    }

    public Incident retryIncidentDelivery(String id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Incident not found: " + id));

        if (!"DELIVERY_FAILED".equals(incident.getProcessingStatus())) {
            throw new IllegalStateException(
                    "Only incidents with DELIVERY_FAILED status can be retried.");
        }

        // Resend the existing incident with the same ID.
        incidentQueueService.sendIncidentMessage(incident);

        // Update status only after SQS accepts the message.
        incident.setProcessingStatus("PENDING");
        incidentRepository.save(incident);

        return incident;
    }

    public List<Incident> getAllIncidents() {
        return incidentRepository.findAll();
    }

    public List<Incident> getIncidentsBySeverity(String severity) {
        return incidentRepository.findAll()
                .stream()
                .filter(incident ->
                        incident.getSeverity().equalsIgnoreCase(severity))
                .collect(Collectors.toList());
    }

    public List<Incident> getIncidentsByService(String service) {
        return incidentRepository.findAll()
                .stream()
                .filter(incident ->
                        incident.getService().equalsIgnoreCase(service))
                .collect(Collectors.toList());
    }

    public Incident acknowledgeIncident(String id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Incident not found: " + id));

        incident.setStatus("ACKNOWLEDGED");
        incidentRepository.save(incident);

        return incident;
    }
}
