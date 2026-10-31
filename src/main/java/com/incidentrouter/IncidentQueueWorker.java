
package com.incidentrouter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.Set;

@Service
public class IncidentQueueWorker {

    private static final String QUEUE_URL =
            "https://sqs.us-east-1.amazonaws.com/893089245401/incident-alerts";

    private static final Set<String> VALID_SEVERITIES =
            Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final IncidentRepository incidentRepository;

    public IncidentQueueWorker(
            SqsClient sqsClient,
            ObjectMapper objectMapper,
            IncidentRepository incidentRepository) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.incidentRepository = incidentRepository;
    }

    public int processAvailableMessages() {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .maxNumberOfMessages(5)
                .waitTimeSeconds(10)
                .build();

        List<Message> messages =
                sqsClient.receiveMessage(request).messages();

        System.out.println(
                "SQS worker received " + messages.size() + " message(s)");

        int processed = 0;

        for (Message message : messages) {
            try {
                String incidentId = processMessage(message);

                Incident incident = incidentRepository
                        .findById(incidentId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Incident not found in DynamoDB: " + incidentId));

                if (!"PROCESSED".equals(incident.getProcessingStatus())) {
                    incidentRepository.updateProcessingStatus(
                            incidentId, "PROCESSED"
                    ).orElseThrow(() -> new IllegalStateException(
                            "Unable to update incident: " + incidentId));

                    System.out.println(
                            "Incident marked PROCESSED: " + incidentId);
                } else {
                    System.out.println(
                            "Duplicate delivery; already processed: " + incidentId);
                }

                // Delete only after the incident exists and is processed.
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(QUEUE_URL)
                        .receiptHandle(message.receiptHandle())
                        .build());

                processed++;

            } catch (Exception exception) {
                System.err.println(
                        "Failed to process message " + message.messageId()
                                + ": " + exception.getMessage());
                // Leave the message for retry.
            }
        }

        return processed;
    }

    private String processMessage(Message message) throws Exception {
        String body = message.body();

        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException(
                    "Incident message cannot be empty");
        }

        JsonNode incident = objectMapper.readTree(body);

        String id = requireText(incident, "id");
        requireText(incident, "title");
        requireText(incident, "service");
        requireText(incident, "status");

        String severity = requireText(incident, "severity");

        if (!VALID_SEVERITIES.contains(severity)) {
            throw new IllegalArgumentException(
                    "Invalid incident severity: " + severity);
        }

        System.out.println(
                "Validated incident: id=" + id
                        + ", service=" + incident.get("service").asText()
                        + ", severity=" + severity);

        return id;
    }

    private String requireText(JsonNode incident, String field) {
        JsonNode value = incident.get(field);

        if (value == null || !value.isTextual()
                || value.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Missing or invalid incident field: " + field);
        }

        return value.asText();
    }
}
