
package com.incidentrouter;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class IncidentSqsLambdaHandler
        implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final Set<String> VALID_SEVERITIES =
            Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final DynamoDbClient dynamoDbClient;
    private final IncidentRepository incidentRepository;
    private final ObjectMapper objectMapper;

    public IncidentSqsLambdaHandler() {
        this.dynamoDbClient = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .build();

        DynamoDbEnhancedClient enhancedClient =
                DynamoDbEnhancedClient.builder()
                        .dynamoDbClient(dynamoDbClient)
                        .build();

        this.incidentRepository =
                new IncidentRepository(enhancedClient);

        this.objectMapper = new ObjectMapper();
    }

    @Override
    public SQSBatchResponse handleRequest(
            SQSEvent event, Context context) {

        List<SQSBatchResponse.BatchItemFailure> failures =
                new ArrayList<>();

        if (event == null || event.getRecords() == null) {
            return new SQSBatchResponse(failures);
        }

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            String messageId = message.getMessageId();

            try {
                processMessage(message.getBody());

                log(context, "Successfully processed message: " + messageId);

            } catch (Exception exception) {
                failures.add(
                        new SQSBatchResponse.BatchItemFailure(messageId));

                log(context, "Failed message: " + messageId
                        + "; reason: " + exception.getMessage());
            }
        }

        // With ReportBatchItemFailures enabled on the SQS event source
        // mapping, only these failed messages will be retried.
        return new SQSBatchResponse(failures);
    }

    private void processMessage(String body) throws Exception {

        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException(
                    "Incident message cannot be empty");
        }

        JsonNode json = objectMapper.readTree(body);

        String id = requireText(json, "id");
        requireText(json, "title");
        requireText(json, "service");
        requireText(json, "status");

        String severity = requireText(json, "severity");

        if (!VALID_SEVERITIES.contains(severity)) {
            throw new IllegalArgumentException(
                    "Invalid incident severity: " + severity);
        }

        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Incident not found in DynamoDB: " + id));

        String currentStatus = incident.getProcessingStatus();

        // Duplicate SQS delivery is safe when the incident is already done.
        if ("PROCESSED".equals(currentStatus)) {
            return;
        }

        // Do not turn failed or unknown states into successful processing.
        if (!"PENDING".equals(currentStatus)) {
            throw new IllegalStateException(
                    "Incident is not pending. ID: " + id
                            + ", processing status: " + currentStatus);
        }

        Incident updated = incidentRepository
                .updateProcessingStatus(id, "PROCESSED")
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to update incident: " + id));

        // Verify the repository returned the confirmed final status.
        if (!"PROCESSED".equals(updated.getProcessingStatus())) {
            throw new IllegalStateException(
                    "Incident processing was not confirmed: " + id
                            + ", current status: "
                            + updated.getProcessingStatus());
        }
    }

    private String requireText(JsonNode json, String field) {

        JsonNode value = json.get(field);

        if (value == null || !value.isTextual()
                || value.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Missing or invalid incident field: " + field);
        }

        return value.asText();
    }

    private void log(Context context, String message) {
        if (context != null) {
            context.getLogger().log(message + "\n");
        }
    }
}
