
package com.incidentrouter;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class IncidentEscalationLambdaHandler
        implements RequestHandler<Map<String, Object>, String> {

    private final IncidentEscalationService escalationService;

    public IncidentEscalationLambdaHandler() {

        DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        DynamoDbEnhancedClient enhancedClient =
                DynamoDbEnhancedClient.builder()
                        .dynamoDbClient(dynamoDbClient)
                        .build();

        IncidentRepository repository =
                new IncidentRepository(enhancedClient);

        this.escalationService =
                new IncidentEscalationService(repository);
    }

    @Override
    public String handleRequest(
            Map<String, Object> event,
            Context context) {

        context.getLogger().log(
                "Starting incident escalation check...\n"
        );

        int count = escalationService.escalateOverdueIncidents();

        String result = "Escalation check completed. Total escalated: " + count;

        context.getLogger().log(result + "\n");

        return result;
    }
}
