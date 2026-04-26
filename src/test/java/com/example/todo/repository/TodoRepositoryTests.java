package com.example.todo.repository;

import com.example.todo.model.Todo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TodoRepositoryTests {

    @Autowired
    private TodoRepository todoRepository;

    @Test
    void saveShouldPopulateAuditFieldsAndVersion() {
        Todo todo = new Todo();
        todo.setTitle("Initial title");
        todo.setDescription("Initial description");
        todo.setCompleted(false);

        Todo saved = todoRepository.saveAndFlush(todo);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isNotNull();
    }

    @Test
    void updateShouldIncreaseVersionAndRefreshUpdatedAt() {
        Todo todo = new Todo();
        todo.setTitle("Initial title");
        todo.setDescription("Initial description");
        todo.setCompleted(false);

        Todo saved = todoRepository.saveAndFlush(todo);
        Long initialVersion = saved.getVersion();

        saved.setCompleted(true);
        Todo updated = todoRepository.saveAndFlush(saved);

        assertThat(updated.getVersion()).isGreaterThan(initialVersion);
        assertThat(updated.getUpdatedAt()).isNotNull();
        assertThat(updated.getCreatedAt()).isNotNull();
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updated.getCreatedAt());
    }
}
