package com.example.todo.service;

import com.example.todo.exception.TodoNotFoundException;
import com.example.todo.event.TodoCreatedEvent;
import com.example.todo.model.Todo;
import com.example.todo.repository.TodoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TodoService {

    private static final Logger log = LoggerFactory.getLogger(TodoService.class);

    private final TodoRepository todoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TodoService(TodoRepository todoRepository, ApplicationEventPublisher eventPublisher) {
        this.todoRepository = todoRepository;
        this.eventPublisher = eventPublisher;
    }

    public List<Todo> getAll() {
        List<Todo> todos = todoRepository.findAll();
        log.debug("Fetched todos. count={}", todos.size());
        return todos;
    }

    public Todo getById(Long id) {
        return todoRepository.findById(id).orElseThrow(() -> {
            log.warn("Todo not found. id={}", id);
            return new TodoNotFoundException(id);
        });
    }

    @Transactional
    public Todo create(Todo todo) {
        log.info("Creating todo. title='{}', completed={}", todo.getTitle(), todo.isCompleted());
        todo.setId(null);
        Todo savedTodo = todoRepository.save(todo);
        eventPublisher.publishEvent(new TodoCreatedEvent(
                savedTodo.getId(),
                savedTodo.getTitle(),
                savedTodo.getDescription(),
                savedTodo.isCompleted(),
                savedTodo.getAssignee(),
                savedTodo.getLabels(),
                savedTodo.getCreatedAt(),
                savedTodo.getUpdatedAt(),
                savedTodo.getVersion()
        ));
        log.info("Todo created successfully. id={}, version={}", savedTodo.getId(), savedTodo.getVersion());
        return savedTodo;
    }

    public Todo update(Long id, Todo todo) {
        log.info("Updating todo. id={}", id);
        Todo existing = getById(id);
        existing.setTitle(todo.getTitle());
        existing.setDescription(todo.getDescription());
        existing.setCompleted(todo.isCompleted());
        existing.setAssignee(todo.getAssignee());
        existing.setLabels(todo.getLabels());
        Todo updated = todoRepository.save(existing);
        log.info("Todo updated successfully. id={}, version={}", updated.getId(), updated.getVersion());
        return updated;
    }

    public void delete(Long id) {
        log.info("Deleting todo. id={}", id);
        Todo existing = getById(id);
        todoRepository.delete(existing);
        log.info("Todo deleted successfully. id={}", id);
    }
}
