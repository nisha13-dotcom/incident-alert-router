
package com.incidentrouter;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

class IncidentAlertRouterApplicationTests {

    private IncidentService newService(
            IncidentRepository repository,
            IncidentQueueService queueService) {
        return newService(
                repository,
                queueService,
                mock(IncidentFingerprintRepository.class),
                mock(IncidentCreationTransaction.class));
    }

    private IncidentService newService(
            IncidentRepository repository,
            IncidentQueueService queueService,
            IncidentFingerprintRepository fingerprintRepository,
            IncidentCreationTransaction creationTransaction) {
        return new IncidentService(
                repository,
                queueService,
                fingerprintRepository,
                creationTransaction);
    }

    @Test
    void createIncidentShouldAssignMetadataAndStoreIncident() {
        IncidentRepository repository = mock(IncidentRepository.class);
        IncidentQueueService queueService = mock(IncidentQueueService.class);
        IncidentCreationTransaction transaction =
                mock(IncidentCreationTransaction.class);

        IncidentService service = newService(
                repository, queueService,
                mock(IncidentFingerprintRepository.class), transaction);

        Incident incident = new Incident(
                "Database connection failure",
                "payment-service", "HIGH");

        Incident created = service.createIncident(incident);

        verify(transaction).createIncidentAndClaimFingerprint(incident);
        verify(queueService).sendIncidentMessage(incident);
        verify(repository, never()).save(incident);

        assertNotNull(created.getId());
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getFingerprint());
        assertEquals("NEW", created.getStatus());
        assertEquals("Database connection failure", created.getTitle());
    }

    @Test
    void createTwoIncidentsShouldCreateBothTransactions() {
        IncidentRepository repository = mock(IncidentRepository.class);
        IncidentQueueService queueService = mock(IncidentQueueService.class);
        IncidentCreationTransaction transaction =
                mock(IncidentCreationTransaction.class);

        IncidentService service = newService(
                repository, queueService,
                mock(IncidentFingerprintRepository.class), transaction);

        Incident first = new Incident(
                "Database failure", "payment-service", "HIGH");
        Incident second = new Incident(
                "API timeout", "user-service", "LOW");

        service.createIncident(first);
        service.createIncident(second);

        verify(transaction, times(2))
                .createIncidentAndClaimFingerprint(any(Incident.class));
        verify(queueService, times(2))
                .sendIncidentMessage(any(Incident.class));
    }

    @Test
    void filterBySeverityShouldReturnOnlyMatchingIncidents() {
        IncidentRepository repository = mock(IncidentRepository.class);
        IncidentQueueService queueService = mock(IncidentQueueService.class);
        IncidentService service = newService(repository, queueService);

        Incident high = new Incident(
                "Database failure", "payment-service", "HIGH");
        Incident low = new Incident(
                "Minor warning", "user-service", "LOW");

        when(repository.findAll()).thenReturn(List.of(high, low));

        List<Incident> result = service.getIncidentsBySeverity("HIGH");

        assertEquals(1, result.size());
        assertEquals("HIGH", result.get(0).getSeverity());
    }

    @Test
    void acknowledgeIncidentShouldChangeStatus() {
        IncidentRepository repository = mock(IncidentRepository.class);
        IncidentQueueService queueService = mock(IncidentQueueService.class);
        IncidentService service = newService(repository, queueService);

        Incident incident = new Incident(
                "Database failure", "payment-service", "HIGH");
        incident.assignMetadata();

        when(repository.findById(incident.getId()))
                .thenReturn(Optional.of(incident));

        Incident acknowledged =
                service.acknowledgeIncident(incident.getId());

        assertEquals("ACKNOWLEDGED", acknowledged.getStatus());
        verify(repository).save(incident);
        verifyNoInteractions(queueService);
    }

    @Test
    void acknowledgeUnknownIncidentShouldThrowException() {
        IncidentRepository repository = mock(IncidentRepository.class);
        IncidentQueueService queueService = mock(IncidentQueueService.class);
        IncidentService service = newService(repository, queueService);

        when(repository.findById("unknown-id"))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> service.acknowledgeIncident("unknown-id"));
    }

    @Test
    void createIncidentWithBlankTitleShouldFailValidation() {
        Incident incident = new Incident();
        incident.setTitle("");
        incident.setService("payment-service");
        incident.setSeverity("HIGH");

        try (ValidatorFactory factory =
                     Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertFalse(validator.validate(incident).isEmpty());
        }
    }

    @Test
    void workerShouldProcessValidMessageAndDeleteIt() {
        SqsClient sqsClient = mock(SqsClient.class);
        IncidentRepository repository = mock(IncidentRepository.class);

        Message message = Message.builder()
                .messageId("message-123")
                .receiptHandle("receipt-123")
                .body("""
                        {
                          "id":"test-123",
                          "title":"Database failure",
                          "service":"payment-service",
                          "severity":"HIGH",
                          "status":"NEW"
                        }
                        """)
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder()
                        .messages(message).build());

        Incident storedIncident = new Incident(
                "Database failure", "payment-service", "HIGH");
        storedIncident.setId("test-123");

        when(repository.findById("test-123"))
                .thenReturn(Optional.of(storedIncident));
        when(repository.updateProcessingStatus("test-123", "PROCESSED"))
                .thenReturn(Optional.of(storedIncident));

        IncidentQueueWorker worker =
                new IncidentQueueWorker(
                        sqsClient, new ObjectMapper(), repository);

        int processed = worker.processAvailableMessages();

        assertEquals(1, processed);
        verify(repository).findById("test-123");
        verify(repository).updateProcessingStatus("test-123", "PROCESSED");
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void workerShouldNotDeleteMessageWhenJsonIsMalformed() {
        SqsClient sqsClient = mock(SqsClient.class);
        IncidentRepository repository = mock(IncidentRepository.class);

        Message message = Message.builder()
                .messageId("bad-message")
                .receiptHandle("receipt-bad")
                .body("{status:NEW,title:Malformed}")
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder()
                        .messages(message).build());

        IncidentQueueWorker worker =
                new IncidentQueueWorker(
                        sqsClient, new ObjectMapper(), repository);

        assertEquals(0, worker.processAvailableMessages());
        verify(sqsClient, never())
                .deleteMessage(any(DeleteMessageRequest.class));
        verifyNoInteractions(repository);
    }

    @Test
    void workerShouldNotDeleteMessageWhenIncidentIsMissingFromDatabase() {
        SqsClient sqsClient = mock(SqsClient.class);
        IncidentRepository repository = mock(IncidentRepository.class);

        Message message = Message.builder()
                .messageId("missing-incident")
                .receiptHandle("receipt-missing")
                .body("""
                        {
                          "id":"not-in-database",
                          "title":"Database failure",
                          "service":"payment-service",
                          "severity":"HIGH",
                          "status":"NEW"
                        }
                        """)
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder()
                        .messages(message).build());
        when(repository.findById("not-in-database"))
                .thenReturn(Optional.empty());

        IncidentQueueWorker worker =
                new IncidentQueueWorker(
                        sqsClient, new ObjectMapper(), repository);

        assertEquals(0, worker.processAvailableMessages());
        verify(repository).findById("not-in-database");
        verify(repository, never())
                .updateProcessingStatus(anyString(), anyString());
        verify(sqsClient, never())
                .deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void workerShouldNotUpdateAlreadyProcessedIncident() {
        SqsClient sqsClient = mock(SqsClient.class);
        IncidentRepository repository = mock(IncidentRepository.class);

        Message message = Message.builder()
                .messageId("duplicate-message")
                .receiptHandle("receipt-duplicate")
                .body("""
                        {
                          "id":"already-processed-123",
                          "title":"Database failure",
                          "service":"payment-service",
                          "severity":"HIGH",
                          "status":"NEW"
                        }
                        """)
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder()
                        .messages(message).build());

        Incident storedIncident = new Incident(
                "Database failure", "payment-service", "HIGH");
        storedIncident.setId("already-processed-123");
        storedIncident.setProcessingStatus("PROCESSED");

        when(repository.findById("already-processed-123"))
                .thenReturn(Optional.of(storedIncident));

        IncidentQueueWorker worker =
                new IncidentQueueWorker(
                        sqsClient, new ObjectMapper(), repository);

        assertEquals(1, worker.processAvailableMessages());
        verify(repository).findById("already-processed-123");
        verify(repository, never())
                .updateProcessingStatus(anyString(), anyString());
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void createIncidentShouldMarkDeliveryFailedWhenSqsFails() {
        IncidentRepository repository = mock(IncidentRepository.class);
        IncidentQueueService queueService = mock(IncidentQueueService.class);
        IncidentCreationTransaction transaction =
                mock(IncidentCreationTransaction.class);

        IncidentService service = newService(
                repository, queueService,
                mock(IncidentFingerprintRepository.class), transaction);

        doThrow(new IllegalStateException("Simulated SQS failure"))
                .when(queueService)
                .sendIncidentMessage(any(Incident.class));

        Incident incident = new Incident(
                "SQS delivery failure test", "test-service", "LOW");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.createIncident(incident));

        assertTrue(exception.getMessage().contains("SQS delivery failed"));
        assertEquals("DELIVERY_FAILED", incident.getProcessingStatus());

        verify(transaction).createIncidentAndClaimFingerprint(incident);
        verify(repository).save(incident);
        verify(queueService).sendIncidentMessage(incident);
    }

    @Test
    void retryDeliveryShouldResendFailedIncident() {
        IncidentRepository repository = mock(IncidentRepository.class);
        IncidentQueueService queueService = mock(IncidentQueueService.class);
        IncidentService service = newService(repository, queueService);

        Incident incident = new Incident(
                "Retry test", "test-service", "LOW");
        incident.setId("retry-test-123");
        incident.setProcessingStatus("DELIVERY_FAILED");

        when(repository.findById("retry-test-123"))
                .thenReturn(Optional.of(incident));

        Incident result = service.retryIncidentDelivery("retry-test-123");

        verify(queueService).sendIncidentMessage(incident);
        verify(repository).save(incident);
        assertEquals("PENDING", result.getProcessingStatus());
    }

    @Test
    void retryDeliveryShouldRejectIncidentNotMarkedAsFailed() {
        IncidentRepository repository = mock(IncidentRepository.class);
        IncidentQueueService queueService = mock(IncidentQueueService.class);
        IncidentService service = newService(repository, queueService);

        Incident incident = new Incident(
                "Pending incident", "test-service", "LOW");
        incident.setId("pending-test-123");
        incident.setProcessingStatus("PENDING");

        when(repository.findById("pending-test-123"))
                .thenReturn(Optional.of(incident));

        assertThrows(IllegalStateException.class,
                () -> service.retryIncidentDelivery("pending-test-123"));

        verifyNoInteractions(queueService);
        verify(repository, never()).save(any(Incident.class));
    }

    @Test
    void retryDeliveryShouldRejectUnknownIncident() {
        IncidentRepository repository = mock(IncidentRepository.class);
        IncidentQueueService queueService = mock(IncidentQueueService.class);
        IncidentService service = newService(repository, queueService);

        when(repository.findById("missing-retry-id"))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> service.retryIncidentDelivery("missing-retry-id"));

        verifyNoInteractions(queueService);
        verify(repository, never()).save(any(Incident.class));
    }

    @Test
    void retryDeliveryShouldKeepFailedStatusWhenSqsFails() {
        IncidentRepository repository = mock(IncidentRepository.class);
        IncidentQueueService queueService = mock(IncidentQueueService.class);
        IncidentService service = newService(repository, queueService);

        Incident incident = new Incident(
                "Retry failure test", "test-service", "LOW");
        incident.setId("retry-failure-123");
        incident.setProcessingStatus("DELIVERY_FAILED");

        when(repository.findById("retry-failure-123"))
                .thenReturn(Optional.of(incident));
        doThrow(new IllegalStateException("Simulated SQS failure"))
                .when(queueService).sendIncidentMessage(incident);

        assertThrows(IllegalStateException.class,
                () -> service.retryIncidentDelivery("retry-failure-123"));

        assertEquals("DELIVERY_FAILED", incident.getProcessingStatus());
        verify(queueService).sendIncidentMessage(incident);
        verify(repository, never()).save(any(Incident.class));
    }

    @Test
    void duplicateFingerprintShouldReturnExistingIncident() {
        IncidentRepository repository = mock(IncidentRepository.class);
        IncidentQueueService queueService = mock(IncidentQueueService.class);
        IncidentFingerprintRepository fingerprintRepository =
                mock(IncidentFingerprintRepository.class);
        IncidentCreationTransaction transaction =
                mock(IncidentCreationTransaction.class);

        IncidentService service = newService(
                repository, queueService, fingerprintRepository, transaction);

        Incident existing = new Incident(
                "Database connection failure", "payment-service", "HIGH");
        existing.setId("existing-incident-123");

        Incident incoming = new Incident(
                "Database connection failure", "payment-service", "HIGH");
        incoming.assignMetadata();
        incoming.setFingerprint(IncidentFingerprint.generate(incoming));

        when(transaction.isFingerprintAlreadyClaimed(anyString()))
                .thenReturn(true);
        when(fingerprintRepository.findIncidentId(anyString()))
                .thenReturn(Optional.of("existing-incident-123"));
        when(repository.findById("existing-incident-123"))
                .thenReturn(Optional.of(existing));

        doThrow(mock(TransactionCanceledException.class))
                .when(transaction)
                .createIncidentAndClaimFingerprint(any(Incident.class));

        Incident result = service.createIncident(incoming);

        assertEquals("existing-incident-123", result.getId());
        verify(repository, never()).save(incoming);
        verify(queueService, never())
                .sendIncidentMessage(any(Incident.class));
    }

    @Test
    void duplicateFingerprintReferencingMissingIncidentShouldThrow() {
        IncidentRepository repository = mock(IncidentRepository.class);
        IncidentQueueService queueService = mock(IncidentQueueService.class);
        IncidentFingerprintRepository fingerprintRepository =
                mock(IncidentFingerprintRepository.class);
        IncidentCreationTransaction transaction =
                mock(IncidentCreationTransaction.class);

        IncidentService service = newService(
                repository, queueService, fingerprintRepository, transaction);

        when(transaction.isFingerprintAlreadyClaimed(anyString()))
                .thenReturn(true);
        when(fingerprintRepository.findIncidentId(anyString()))
                .thenReturn(Optional.of("missing-incident-123"));
        when(repository.findById("missing-incident-123"))
                .thenReturn(Optional.empty());

        doThrow(mock(TransactionCanceledException.class))
                .when(transaction)
                .createIncidentAndClaimFingerprint(any(Incident.class));

        Incident incoming = new Incident(
                "Database connection failure", "payment-service", "HIGH");

        assertThrows(IllegalStateException.class,
                () -> service.createIncident(incoming));

        verify(queueService, never())
                .sendIncidentMessage(any(Incident.class));
    }

    @Test
    void transactionCancellationWithoutFingerprintShouldBeRethrown() {
        IncidentRepository repository = mock(IncidentRepository.class);
        IncidentQueueService queueService = mock(IncidentQueueService.class);
        IncidentFingerprintRepository fingerprintRepository =
                mock(IncidentFingerprintRepository.class);
        IncidentCreationTransaction transaction =
                mock(IncidentCreationTransaction.class);

        IncidentService service = newService(
                repository, queueService, fingerprintRepository, transaction);

        when(transaction.isFingerprintAlreadyClaimed(anyString()))
                .thenReturn(false);

        TransactionCanceledException cancellation =
                mock(TransactionCanceledException.class);

        doThrow(cancellation)
                .when(transaction)
                .createIncidentAndClaimFingerprint(any(Incident.class));

        Incident incoming = new Incident(
                "New API timeout", "user-service", "MEDIUM");

        assertThrows(TransactionCanceledException.class,
                () -> service.createIncident(incoming));

        verify(queueService, never())
                .sendIncidentMessage(any(Incident.class));
    }

    @Test
    void newFingerprintShouldUseTransactionBeforeSendingToSqs() {
        IncidentRepository repository = mock(IncidentRepository.class);
        IncidentQueueService queueService = mock(IncidentQueueService.class);
        IncidentFingerprintRepository fingerprintRepository =
                mock(IncidentFingerprintRepository.class);
        IncidentCreationTransaction transaction =
                mock(IncidentCreationTransaction.class);

        IncidentService service = newService(
                repository, queueService, fingerprintRepository, transaction);

        Incident incoming = new Incident(
                "New API timeout", "user-service", "MEDIUM");

        Incident result = service.createIncident(incoming);

        assertNotNull(result.getId());
        assertNotNull(result.getFingerprint());

        verify(transaction).createIncidentAndClaimFingerprint(incoming);
        verify(queueService).sendIncidentMessage(incoming);
        verifyNoInteractions(fingerprintRepository);
        verify(repository, never()).save(incoming);
    }
}
