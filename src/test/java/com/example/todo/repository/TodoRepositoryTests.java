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
        todo.setAssignee("alice");
        todo.setLabels(java.util.List.of("backend", "urgent"));

        Todo saved = todoRepository.saveAndFlush(todo);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAssignee()).isEqualTo("alice");
        assertThat(saved.getLabels()).containsExactly("backend", "urgent");
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
        todo.setAssignee("alice");
        todo.setLabels(java.util.List.of("backend"));

        Todo saved = todoRepository.saveAndFlush(todo);
        Long initialVersion = saved.getVersion();

        saved.setCompleted(true);
        saved.setAssignee("bob");
        saved.setLabels(java.util.List.of("backend", "done"));
        Todo updated = todoRepository.saveAndFlush(saved);

        assertThat(updated.getAssignee()).isEqualTo("bob");
        assertThat(updated.getLabels()).containsExactly("backend", "done");
        assertThat(updated.getVersion()).isGreaterThan(initialVersion);
        assertThat(updated.getUpdatedAt()).isNotNull();
        assertThat(updated.getCreatedAt()).isNotNull();
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updated.getCreatedAt());
    }
}
