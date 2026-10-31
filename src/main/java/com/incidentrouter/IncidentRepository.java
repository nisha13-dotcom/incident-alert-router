
package com.incidentrouter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Repository
public class IncidentRepository {

    private static final String TABLE_NAME = "IncidentAlerts";

    private final DynamoDbTable<Incident> incidentTable;

    public IncidentRepository(DynamoDbEnhancedClient enhancedClient) {
        this.incidentTable = enhancedClient.table(
                TABLE_NAME,
                TableSchema.fromBean(Incident.class)
        );
    }

    public Incident save(Incident incident) {
        incidentTable.putItem(incident);
        return incident;
    }

    public Optional<Incident> findById(String id) {
        Incident incident = incidentTable.getItem(
                Key.builder().partitionValue(id).build()
        );
        return Optional.ofNullable(incident);
    }

    public List<Incident> findAll() {
        List<Incident> incidents = new ArrayList<>();
        incidentTable.scan().items().forEach(incidents::add);
        return incidents;
    }

    public Optional<Incident> updateProcessingStatus(
            String id, String processingStatus) {

        if (!"PROCESSED".equals(processingStatus)) {
            throw new IllegalArgumentException(
                    "Unsupported processing status: " + processingStatus);
        }

        Optional<Incident> result = findById(id);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        Incident incident = result.get();

        if ("PROCESSED".equals(incident.getProcessingStatus())) {
            return result;
        }

        Expression condition = Expression.builder()
                .expression("processingStatus = :pending")
                .putExpressionValue(
                        ":pending",
                        AttributeValue.builder().s("PENDING").build()
                )
                .build();

        incident.setProcessingStatus("PROCESSED");

        UpdateItemEnhancedRequest<Incident> request =
                UpdateItemEnhancedRequest.builder(Incident.class)
                        .item(incident)
                        .conditionExpression(condition)
                        .build();

        try {
            Incident updatedIncident = incidentTable.updateItem(request);
            return Optional.of(updatedIncident);

        } catch (ConditionalCheckFailedException exception) {
            return findById(id);
        }
    }

    // Find incidents eligible for escalation
    public List<Incident> findIncidentsForEscalation(Instant cutoff) {

        List<Incident> eligibleIncidents = new ArrayList<>();

        for (Incident incident : findAll()) {

            if ("NEW".equals(incident.getStatus())
                    && "PROCESSED".equals(incident.getProcessingStatus())
                    && incident.getCreatedAt() != null
                    && !incident.getCreatedAt().isAfter(cutoff)
                    && incident.getEscalatedAt() == null) {

                eligibleIncidents.add(incident);
            }
        }

        return eligibleIncidents;
    }

    // Escalate an incident only if it is still eligible
    public Optional<Incident> escalateIncident(
            Incident incident, Instant cutoff) {

        Expression condition = Expression.builder()
                .expression(
                        "#incidentStatus = :newStatus " +
                        "AND processingStatus = :processed " +
                        "AND createdAt <= :cutoff " +
                        "AND attribute_not_exists(escalatedAt)"
                )
                .putExpressionName(
                        "#incidentStatus", "status"
                )
                .putExpressionValue(
                        ":newStatus",
                        AttributeValue.builder().s("NEW").build()
                )
                .putExpressionValue(
                        ":processed",
                        AttributeValue.builder().s("PROCESSED").build()
                )
                .putExpressionValue(
                        ":cutoff",
                        AttributeValue.builder().s(cutoff.toString()).build()
                )
                .build();

        incident.setStatus("ESCALATED");
        incident.setEscalatedAt(Instant.now());

        UpdateItemEnhancedRequest<Incident> request =
                UpdateItemEnhancedRequest.builder(Incident.class)
                        .item(incident)
                        .conditionExpression(condition)
                        .build();

        try {
            return Optional.of(incidentTable.updateItem(request));

        } catch (ConditionalCheckFailedException exception) {
            // Incident may have been acknowledged or changed concurrently.
            return Optional.empty();
        }
    }
}
