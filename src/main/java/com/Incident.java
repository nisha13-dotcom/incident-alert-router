
package com.incidentrouter;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@DynamoDbBean
public class Incident {

    private String id;
    private Instant createdAt;
    private String status = "NEW";
    private String processingStatus = "PENDING";
    private String fingerprint;
    private Instant escalatedAt;

    @NotBlank(message = "Title cannot be empty")
    private String title;

    @NotBlank(message = "Service cannot be empty")
    private String service;

    @NotBlank(message = "Severity cannot be empty")
    @Pattern(
        regexp = "LOW|MEDIUM|HIGH|CRITICAL",
        message = "Severity must be LOW, MEDIUM, HIGH, or CRITICAL"
    )
    private String severity;

    public Incident() {
    }

    public Incident(String title, String service, String severity) {
        this.title = title;
        this.service = service;
        this.severity = severity;
    }

    public void assignMetadata() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }

        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public Instant getEscalatedAt() {
        return escalatedAt;
    }

    public void setEscalatedAt(Instant escalatedAt) {
        this.escalatedAt = escalatedAt;
    }
}
