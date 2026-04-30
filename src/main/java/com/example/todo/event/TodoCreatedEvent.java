package com.example.todo.event;

import java.time.LocalDateTime;
import java.util.List;

public record TodoCreatedEvent(
        Long id,
        String title,
        String description,
        boolean completed,
        String assignee,
        List<String> labels,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version
) {
}
