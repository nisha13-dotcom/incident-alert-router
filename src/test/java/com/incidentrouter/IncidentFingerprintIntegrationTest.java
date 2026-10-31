
package com.incidentrouter;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import static org.junit.jupiter.api.Assertions.*;

class IncidentFingerprintIntegrationTest {

    @Test
    void shouldClaimAndDetectDuplicateInRealDynamoDb() {

        String fingerprint = "integration-test-" + UUID.randomUUID();
        String incidentId = UUID.randomUUID().toString();

        try (DynamoDbClient client = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        ProfileCredentialsProvider.create(
                                "incident-router-dev"))
                .build()) {

            DynamoDbEnhancedClient enhancedClient =
                    DynamoDbEnhancedClient.builder()
                            .dynamoDbClient(client)
                            .build();

            IncidentFingerprintRepository repository =
                    new IncidentFingerprintRepository(enhancedClient);

            boolean claimed = false;

            try {
                Optional<String> first =
                        repository.claimFingerprint(
                                fingerprint, incidentId);

                assertTrue(first.isEmpty());
                claimed = true;

                Optional<String> duplicate =
                        repository.claimFingerprint(
                                fingerprint, "another-incident-id");

                assertEquals(
                        Optional.of(incidentId),
                        duplicate);

                assertEquals(
                        Optional.of(incidentId),
                        repository.findIncidentId(fingerprint));

            } finally {
                if (claimed) {
                    repository.deleteClaim(
                            fingerprint, incidentId);
                }
            }
        }
    }
}
