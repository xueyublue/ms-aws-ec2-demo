package com.example.todo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@ConditionalOnExpression("${app.sqs.enabled:false} or ${app.sqs.consumer.enabled:false}")
public class SqsConfig {

    private static final Logger log = LoggerFactory.getLogger(SqsConfig.class);

    @Bean
    public SqsClient sqsClient(@Value("${app.sqs.region}") String region) {
        log.info("Initializing AWS SQS client. region={}", region);
        return SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }
}
