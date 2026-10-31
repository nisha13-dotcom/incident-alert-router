
package com.incidentrouter;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Repository;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

@Repository
public class IncidentCreationTransaction {

    private static final String INCIDENT_TABLE = "IncidentAlerts";
    private static final String FINGERPRINT_TABLE =
            "IncidentFingerprintIndex";

    private final DynamoDbClient dynamoDbClient;

    public IncidentCreationTransaction(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    public void createIncidentAndClaimFingerprint(Incident incident) {

        Map<String, AttributeValue> incidentItem = new HashMap<>();
        incidentItem.put("id", stringValue(incident.getId()));
        incidentItem.put("createdAt",
                stringValue(incident.getCreatedAt().toString()));
        incidentItem.put("status", stringValue(incident.getStatus()));
        incidentItem.put("processingStatus",
                stringValue(incident.getProcessingStatus()));
        incidentItem.put("fingerprint",
                stringValue(incident.getFingerprint()));
        incidentItem.put("title", stringValue(incident.getTitle()));
        incidentItem.put("service", stringValue(incident.getService()));
        incidentItem.put("severity", stringValue(incident.getSeverity()));

        Map<String, AttributeValue> fingerprintItem = new HashMap<>();
        fingerprintItem.put("fingerprint",
                stringValue(incident.getFingerprint()));
        fingerprintItem.put("incidentId", stringValue(incident.getId()));
        fingerprintItem.put("claimStatus", stringValue("CLAIMED"));
        fingerprintItem.put("claimedAtEpochSeconds",
                AttributeValue.builder()
                        .n(String.valueOf(
                                incident.getCreatedAt().getEpochSecond()))
                        .build());

        TransactWriteItem saveIncident = TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(INCIDENT_TABLE)
                        .item(incidentItem)
                        .conditionExpression("attribute_not_exists(id)")
                        .build())
                .build();

        TransactWriteItem claimFingerprint = TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(FINGERPRINT_TABLE)
                        .item(fingerprintItem)
                        .conditionExpression(
                                "attribute_not_exists(fingerprint)")
                        .build())
                .build();

        TransactWriteItemsRequest request =
                TransactWriteItemsRequest.builder()
                        .transactItems(saveIncident, claimFingerprint)
                        .build();

        dynamoDbClient.transactWriteItems(request);
    }

    public boolean isFingerprintAlreadyClaimed(String fingerprint) {
        GetItemRequest request = GetItemRequest.builder()
                .tableName(FINGERPRINT_TABLE)
                .key(Map.of(
                        "fingerprint",
                        stringValue(fingerprint)))
                .consistentRead(true)
                .build();

        return dynamoDbClient.getItem(request).hasItem();
    }

    private AttributeValue stringValue(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
