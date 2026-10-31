
package com.incidentrouter;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Repository
public class IncidentFingerprintRepository {

    private static final String TABLE_NAME =
            "IncidentFingerprintIndex";

    private final DynamoDbTable<IncidentFingerprintEntry> table;

    public IncidentFingerprintRepository(
            DynamoDbEnhancedClient enhancedClient) {

        this.table = enhancedClient.table(
                TABLE_NAME,
                TableSchema.fromBean(IncidentFingerprintEntry.class));
    }

    /**
     * Returns the existing incident ID if the fingerprint
     * is already registered. Otherwise, registers this incident
     * and returns an empty Optional.
     */
    public Optional<String> claimFingerprint(
            String fingerprint, String incidentId) {

        IncidentFingerprintEntry entry =
                new IncidentFingerprintEntry();

        entry.setFingerprint(fingerprint);
        entry.setIncidentId(incidentId);

        PutItemEnhancedRequest<IncidentFingerprintEntry> request =
                PutItemEnhancedRequest.builder(
                                IncidentFingerprintEntry.class)
                        .item(entry)
                        .conditionExpression(
                                Expression.builder()
                                        .expression(
                                                "attribute_not_exists(fingerprint)")
                                        .build())
                        .build();

        try {
            table.putItem(request);
            return Optional.empty();

        } catch (ConditionalCheckFailedException exception) {
            return findIncidentId(fingerprint);
        }
    }

    public Optional<String> findIncidentId(String fingerprint) {

        IncidentFingerprintEntry entry = table.getItem(
                Key.builder()
                        .partitionValue(fingerprint)
                        .build());

        return entry == null
                ? Optional.empty()
                : Optional.ofNullable(entry.getIncidentId());
    }

  
public void deleteClaim(String fingerprint, String incidentId) {
    Expression condition = Expression.builder()
            .expression("incidentId = :incidentId")
            .putExpressionValue(
                    ":incidentId",
                    software.amazon.awssdk.services.dynamodb.model.AttributeValue
                            .builder()
                            .s(incidentId)
                            .build())
            .build();

    try {
        table.deleteItem(
                software.amazon.awssdk.enhanced.dynamodb.model
                        .DeleteItemEnhancedRequest.builder()
                        .key(software.amazon.awssdk.enhanced.dynamodb.Key.builder()
                                .partitionValue(fingerprint)
                                .build())
                        .conditionExpression(condition)
                        .build());
    } catch (software.amazon.awssdk.services.dynamodb.model
            .ConditionalCheckFailedException exception) {
        // The claim belongs to another incident now; do not delete it.
    }
}

}
