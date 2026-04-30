package com.example.todo.service;

import com.example.todo.event.TodoCreatedEvent;
import com.example.todo.exception.TodoNotFoundException;
import com.example.todo.model.Todo;
import com.example.todo.repository.TodoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoServiceTests {

    @Mock
    private TodoRepository todoRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TodoService todoService;

    @Test
    void getAllShouldReturnRepositoryData() {
        Todo todo = new Todo();
        todo.setId(1L);
        todo.setTitle("Task");
        when(todoRepository.findAll()).thenReturn(List.of(todo));

        List<Todo> todos = todoService.getAll();

        assertThat(todos).hasSize(1);
        assertThat(todos.getFirst().getId()).isEqualTo(1L);
        verify(todoRepository, times(1)).findAll();
    }

    @Test
    void createShouldResetIdSaveAndPublishEvent() {
        Todo input = new Todo();
        input.setId(999L);
        input.setTitle("Create item");
        input.setDescription("desc");
        input.setCompleted(false);
        input.setAssignee("alice");
        input.setLabels(List.of("backend", "high-priority"));

        Todo saved = new Todo();
        saved.setId(1L);
        saved.setTitle("Create item");
        saved.setDescription("desc");
        saved.setCompleted(false);
        saved.setAssignee("alice");
        saved.setLabels(List.of("backend", "high-priority"));

        when(todoRepository.save(any(Todo.class))).thenReturn(saved);

        Todo result = todoService.create(input);

        ArgumentCaptor<Todo> savedCaptor = ArgumentCaptor.forClass(Todo.class);
        verify(todoRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getId()).isNull();

        ArgumentCaptor<TodoCreatedEvent> eventCaptor = ArgumentCaptor.forClass(TodoCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        TodoCreatedEvent event = eventCaptor.getValue();
        assertThat(event.id()).isEqualTo(1L);
        assertThat(event.title()).isEqualTo("Create item");
        assertThat(event.description()).isEqualTo("desc");
        assertThat(event.completed()).isFalse();
        assertThat(event.assignee()).isEqualTo("alice");
        assertThat(event.labels()).containsExactly("backend", "high-priority");

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getByIdShouldThrowWhenTodoDoesNotExist() {
        when(todoRepository.findById(123L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.getById(123L))
                .isInstanceOf(TodoNotFoundException.class)
                .hasMessageContaining("123");
    }

    @Test
    void updateShouldCopyIncomingFieldsAndSave() {
        Todo existing = new Todo();
        existing.setId(7L);
        existing.setTitle("old");
        existing.setDescription("old description");
        existing.setCompleted(false);
        existing.setAssignee("old-assignee");
        existing.setLabels(List.of("legacy"));

        Todo incoming = new Todo();
        incoming.setTitle("new");
        incoming.setDescription("new description");
        incoming.setCompleted(true);
        incoming.setAssignee("new-assignee");
        incoming.setLabels(List.of("api", "urgent"));

        when(todoRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(todoRepository.save(existing)).thenReturn(existing);

        Todo updated = todoService.update(7L, incoming);

        assertThat(updated.getTitle()).isEqualTo("new");
        assertThat(updated.getDescription()).isEqualTo("new description");
        assertThat(updated.isCompleted()).isTrue();
        assertThat(updated.getAssignee()).isEqualTo("new-assignee");
        assertThat(updated.getLabels()).containsExactly("api", "urgent");
        verify(todoRepository).save(existing);
    }

    @Test
    void deleteShouldRemoveExistingTodo() {
        Todo existing = new Todo();
        existing.setId(10L);
        existing.setTitle("to delete");

        when(todoRepository.findById(10L)).thenReturn(Optional.of(existing));

        todoService.delete(10L);

        verify(todoRepository).delete(existing);
    }
}
