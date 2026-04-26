package com.example.todo.messaging;

import com.example.todo.model.SqsMessageLog;
import com.example.todo.repository.SqsMessageLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(value = "app.sqs.consumer.enabled", havingValue = "true")
public class SqsMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(SqsMessageConsumer.class);

    private final SqsClient sqsClient;
    private final SqsMessageLogRepository sqsMessageLogRepository;
    private final ObjectMapper objectMapper;
    private final String queueUrl;
    private final int maxMessages;
    private final int waitTimeSeconds;

    public SqsMessageConsumer(
            SqsClient sqsClient,
            SqsMessageLogRepository sqsMessageLogRepository,
            ObjectMapper objectMapper,
            @Value("${app.sqs.consumer.queue-url}") String queueUrl,
            @Value("${app.sqs.consumer.max-messages}") int maxMessages,
            @Value("${app.sqs.consumer.wait-time-seconds}") int waitTimeSeconds
    ) {
        this.sqsClient = sqsClient;
        this.sqsMessageLogRepository = sqsMessageLogRepository;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
        this.maxMessages = maxMessages;
        this.waitTimeSeconds = waitTimeSeconds;
    }

    @Scheduled(fixedDelayString = "${app.sqs.consumer.poll-delay-ms}")
    public void pollMessages() {
        if (queueUrl == null || queueUrl.isBlank()) {
            log.error("SQS consumer is enabled but queue URL is missing. property=app.sqs.consumer.queue-url");
            throw new IllegalStateException("SQS consumer is enabled but app.sqs.consumer.queue-url is not set");
        }

        try {
            log.debug("Polling SQS queue. queueUrl={}, maxMessages={}, waitTimeSeconds={}",
                    queueUrl, maxMessages, waitTimeSeconds);
            ReceiveMessageResponse response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(maxMessages)
                    .waitTimeSeconds(waitTimeSeconds)
                    .attributeNamesWithStrings("All")
                    .messageAttributeNames("All")
                    .build());

            if (response.messages().isEmpty()) {
                log.debug("No messages received from SQS queue. queueUrl={}", queueUrl);
                return;
            }

            log.info("Received messages from SQS queue. queueUrl={}, count={}",
                    queueUrl, response.messages().size());
            for (Message message : response.messages()) {
                persistAndDelete(message);
            }
        } catch (SqsException exception) {
            log.error("Failed to receive messages from SQS queue. queueUrl={}", queueUrl, exception);
        }
    }

    private void persistAndDelete(Message message) {
        if (!sqsMessageLogRepository.existsByMessageId(message.messageId())) {
            SqsMessageLog logRecord = new SqsMessageLog();
            logRecord.setMessageId(message.messageId());
            logRecord.setQueueUrl(queueUrl);
            logRecord.setBody(message.body());
            logRecord.setAttributesJson(toJson(message.attributesAsStrings()));
            logRecord.setMessageAttributesJson(toJson(message.messageAttributes()));
            logRecord.setReceivedAt(LocalDateTime.now());
            sqsMessageLogRepository.save(logRecord);
            log.info("Persisted consumed SQS message. messageId={}, queueUrl={}", message.messageId(), queueUrl);
        } else {
            log.warn("Skipped persisting duplicate SQS message. messageId={}, queueUrl={}",
                    message.messageId(), queueUrl);
        }

        deleteMessage(message);
    }

    private void deleteMessage(Message message) {
        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());
            log.debug("Deleted SQS message from queue. messageId={}, queueUrl={}", message.messageId(), queueUrl);
        } catch (SqsException exception) {
            log.error("Failed to delete SQS message after processing. messageId={}", message.messageId(), exception);
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize SQS message metadata", exception);
        }
    }
}
