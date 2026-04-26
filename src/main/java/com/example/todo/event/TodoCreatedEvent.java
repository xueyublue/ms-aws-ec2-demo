package com.example.todo.event;

import java.time.LocalDateTime;

public record TodoCreatedEvent(
        Long id,
        String title,
        String description,
        boolean completed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version
) {
}
