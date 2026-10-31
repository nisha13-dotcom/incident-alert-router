package com.incidentrouter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Service
public class IncidentQueueService {

    private static final String QUEUE_URL =
            "https://sqs.us-east-1.amazonaws.com/893089245401/incident-alerts";

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    public IncidentQueueService(
            SqsClient sqsClient,
            ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
    }

    public void sendIncidentMessage(Incident incident) {
        final String messageBody;

        try {
            messageBody = objectMapper.writeValueAsString(incident);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to convert incident to JSON", exception);
        }

        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .messageBody(messageBody)
                .build();

        sqsClient.sendMessage(request);
    }
}


