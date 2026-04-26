package com.example.todo.messaging;

import com.example.todo.model.SqsMessageLog;
import com.example.todo.repository.SqsMessageLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqsMessageConsumerTests {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private SqsMessageLogRepository sqsMessageLogRepository;

    private SqsMessageConsumer sqsMessageConsumer;

    @BeforeEach
    void setUp() {
        sqsMessageConsumer = new SqsMessageConsumer(
                sqsClient,
                sqsMessageLogRepository,
                new ObjectMapper(),
                "https://sqs.ap-southeast-2.amazonaws.com/123456789012/test-queue",
                10,
                10
        );
    }

    @Test
    void pollMessagesShouldSaveAndDeleteNewMessage() {
        Message message = Message.builder()
                .messageId("mid-1")
                .receiptHandle("rh-1")
                .body("{\"hello\":\"world\"}")
                .attributesWithStrings(Map.of("SenderId", "AIDA_TEST"))
                .build();

        when(sqsClient.receiveMessage(any(software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        when(sqsMessageLogRepository.existsByMessageId("mid-1")).thenReturn(false);
        when(sqsMessageLogRepository.save(any(SqsMessageLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sqsClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(DeleteMessageResponse.builder().build());

        sqsMessageConsumer.pollMessages();

        ArgumentCaptor<SqsMessageLog> logCaptor = ArgumentCaptor.forClass(SqsMessageLog.class);
        verify(sqsMessageLogRepository).save(logCaptor.capture());
        SqsMessageLog saved = logCaptor.getValue();
        assertThat(saved.getMessageId()).isEqualTo("mid-1");
        assertThat(saved.getBody()).contains("hello");
        assertThat(saved.getReceivedAt()).isNotNull();
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void pollMessagesShouldSkipSaveWhenAlreadyLoggedButStillDelete() {
        Message message = Message.builder()
                .messageId("mid-2")
                .receiptHandle("rh-2")
                .body("{}")
                .build();

        when(sqsClient.receiveMessage(any(software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        when(sqsMessageLogRepository.existsByMessageId("mid-2")).thenReturn(true);
        when(sqsClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(DeleteMessageResponse.builder().build());

        sqsMessageConsumer.pollMessages();

        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void pollMessagesShouldFailFastWhenQueueUrlMissing() {
        SqsMessageConsumer consumerWithMissingQueue = new SqsMessageConsumer(
                sqsClient,
                sqsMessageLogRepository,
                new ObjectMapper(),
                "",
                10,
                10
        );

        assertThatThrownBy(consumerWithMissingQueue::pollMessages)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.sqs.consumer.queue-url");
    }
}
