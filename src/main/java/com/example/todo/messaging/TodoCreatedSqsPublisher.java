package com.example.todo.messaging;

import com.example.todo.event.TodoCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@ConditionalOnProperty(value = "app.sqs.enabled", havingValue = "true")
public class TodoCreatedSqsPublisher {

    private static final Logger log = LoggerFactory.getLogger(TodoCreatedSqsPublisher.class);

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String todoCreatedQueueUrl;

    public TodoCreatedSqsPublisher(
            SqsClient sqsClient,
            ObjectMapper objectMapper,
            @Value("${app.sqs.todo-created-queue-url}") String todoCreatedQueueUrl
    ) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.todoCreatedQueueUrl = todoCreatedQueueUrl;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTodoCreated(TodoCreatedEvent event) {
        if (todoCreatedQueueUrl == null || todoCreatedQueueUrl.isBlank()) {
            log.error("SQS publishing is enabled but queue URL is missing. property=app.sqs.todo-created-queue-url");
            throw new IllegalStateException("SQS is enabled but app.sqs.todo-created-queue-url is not set");
        }

        String messageBody = toJson(Map.of(
                "eventType", "TODO_CREATED",
                "sentAt", LocalDateTime.now().toString(),
                "todo", event
        ));

        try {
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(todoCreatedQueueUrl)
                    .messageBody(messageBody)
                    .build());
            log.info("Published TODO_CREATED event to SQS successfully. todoId={}, queueUrl={}",
                    event.id(), todoCreatedQueueUrl);
        } catch (SqsException exception) {
            // DB transaction is already committed at this phase, so avoid failing the request path.
            log.error("Failed to publish TODO_CREATED event to SQS. todoId={}, queueUrl={}",
                    event.id(), todoCreatedQueueUrl, exception);
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize SQS message payload", exception);
        }
    }
}
