package com.example.todo.repository;

import com.example.todo.model.SqsMessageLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SqsMessageLogRepository extends JpaRepository<SqsMessageLog, Long> {

    boolean existsByMessageId(String messageId);
}
