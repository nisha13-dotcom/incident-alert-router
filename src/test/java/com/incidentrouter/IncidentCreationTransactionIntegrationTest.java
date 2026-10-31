
package com.incidentrouter;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import static org.junit.jupiter.api.Assertions.*;

class IncidentCreationTransactionIntegrationTest {

    @Test
    void shouldCreateIncidentAndFingerprintAtomically() {
        String suffix = UUID.randomUUID().toString();

        Incident incident = new Incident(
                "Transaction integration " + suffix,
                "integration-test",
                "LOW");

        incident.assignMetadata();

        String fingerprint = IncidentFingerprint.generate(incident);
        incident.setFingerprint(fingerprint);
        incident.setProcessingStatus("PENDING");

        try (DynamoDbClient client = createDynamoDbClient()) {
            IncidentCreationTransaction transaction =
                    new IncidentCreationTransaction(client);

            IncidentRepository repository = createIncidentRepository(client);

            IncidentFingerprintRepository fingerprintRepository =
                    createFingerprintRepository(client);

            try {
                transaction.createIncidentAndClaimFingerprint(incident);

                assertTrue(
                        repository.findById(incident.getId()).isPresent(),
                        "The incident should be saved");

                assertEquals(
                        Optional.of(incident.getId()),
                        fingerprintRepository.findIncidentId(fingerprint),
                        "The fingerprint should reference the created incident");

            } finally {
                cleanup(
                        client,
                        fingerprintRepository,
                        fingerprint,
                        incident.getId());
            }
        }
    }

    @Test
    void shouldRejectDuplicateFingerprintWithoutCreatingSecondIncident() {
        String suffix = UUID.randomUUID().toString();

        Incident first = new Incident(
                "Duplicate test " + suffix,
                "integration-test",
                "HIGH");

        first.assignMetadata();

        String fingerprint = IncidentFingerprint.generate(first);
        first.setFingerprint(fingerprint);
        first.setProcessingStatus("PENDING");

        Incident second = new Incident(
                first.getTitle(),
                first.getService(),
                first.getSeverity());

        second.assignMetadata();
        second.setFingerprint(fingerprint);
        second.setProcessingStatus("PENDING");

        try (DynamoDbClient client = createDynamoDbClient()) {
            IncidentCreationTransaction transaction =
                    new IncidentCreationTransaction(client);

            IncidentRepository repository = createIncidentRepository(client);

            IncidentFingerprintRepository fingerprintRepository =
                    createFingerprintRepository(client);

            try {
                // First incident should be created successfully.
                transaction.createIncidentAndClaimFingerprint(first);

                // Same fingerprint must not create a second incident.
                assertThrows(
                        TransactionCanceledException.class,
                        () -> transaction.createIncidentAndClaimFingerprint(second),
                        "A duplicate fingerprint should cancel the transaction");

                // The original incident must still exist.
                assertTrue(
                        repository.findById(first.getId()).isPresent(),
                        "The original incident should remain");

                // The second incident must not have been saved.
                assertTrue(
                        repository.findById(second.getId()).isEmpty(),
                        "The duplicate incident should not be created");

                // The fingerprint must still point to the original incident.
                assertEquals(
                        Optional.of(first.getId()),
                        fingerprintRepository.findIncidentId(fingerprint),
                        "The original fingerprint claim should be preserved");

            } finally {
                cleanup(
                        client,
                        fingerprintRepository,
                        fingerprint,
                        first.getId());

                // Safe even if the second incident was never created.
                deleteIncident(client, second.getId());
            }
        }
    }

    private DynamoDbClient createDynamoDbClient() {
        return DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        ProfileCredentialsProvider.create("incident-router-dev"))
                .build();
    }

    private IncidentRepository createIncidentRepository(DynamoDbClient client) {
        DynamoDbEnhancedClient enhancedClient =
                DynamoDbEnhancedClient.builder()
                        .dynamoDbClient(client)
                        .build();

        return new IncidentRepository(enhancedClient);
    }

    private IncidentFingerprintRepository createFingerprintRepository(
            DynamoDbClient client) {

        DynamoDbEnhancedClient enhancedClient =
                DynamoDbEnhancedClient.builder()
                        .dynamoDbClient(client)
                        .build();

        return new IncidentFingerprintRepository(enhancedClient);
    }

    private void cleanup(
            DynamoDbClient client,
            IncidentFingerprintRepository fingerprintRepository,
            String fingerprint,
            String incidentId) {

        try {
            fingerprintRepository.deleteClaim(fingerprint, incidentId);
        } finally {
            deleteIncident(client, incidentId);
        }
    }

    private void deleteIncident(DynamoDbClient client, String incidentId) {
        client.deleteItem(request -> request
                .tableName("IncidentAlerts")
                .key(Map.of(
                        "id",
                        AttributeValue.builder()
                                .s(incidentId)
                                .build())));
    }
}
