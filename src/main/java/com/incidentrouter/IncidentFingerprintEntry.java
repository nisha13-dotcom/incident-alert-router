
package com.incidentrouter;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
public class IncidentFingerprintEntry {

    private String fingerprint;
    private String incidentId;
    private String claimStatus;
    private Long claimedAtEpochSeconds;

    public IncidentFingerprintEntry() {
    }

    @DynamoDbPartitionKey
    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public String getClaimStatus() {
        return claimStatus;
    }

    public void setClaimStatus(String claimStatus) {
        this.claimStatus = claimStatus;
    }

    public Long getClaimedAtEpochSeconds() {
        return claimedAtEpochSeconds;
    }

    public void setClaimedAtEpochSeconds(Long claimedAtEpochSeconds) {
        this.claimedAtEpochSeconds = claimedAtEpochSeconds;
    }
}
