package com.incidentrouter;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

@RestController
public class DynamoDbHealthController {

    private final DynamoDbClient dynamoDbClient;

    public DynamoDbHealthController(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @GetMapping("/api/aws/dynamodb-health")
    public String checkDynamoDb() {

        try {
            DescribeTableResponse response = dynamoDbClient.describeTable(
                    request -> request.tableName("IncidentAlerts")
            );

            return "SUCCESS: Connected to DynamoDB. Table status: "
                    + response.table().tableStatusAsString();

        } catch (ResourceNotFoundException exception) {
            return "ERROR: IncidentAlerts table was not found in this region.";
        }
    }
}

