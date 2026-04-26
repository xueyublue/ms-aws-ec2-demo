package com.example.todo.service;

import com.example.todo.exception.TodoNotFoundException;
import com.example.todo.event.TodoCreatedEvent;
import com.example.todo.model.Todo;
import com.example.todo.repository.TodoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TodoService {

    private final TodoRepository todoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TodoService(TodoRepository todoRepository, ApplicationEventPublisher eventPublisher) {
        this.todoRepository = todoRepository;
        this.eventPublisher = eventPublisher;
    }

    public List<Todo> getAll() {
        return todoRepository.findAll();
    }

    public Todo getById(Long id) {
        return todoRepository.findById(id).orElseThrow(() -> new TodoNotFoundException(id));
    }

    @Transactional
    public Todo create(Todo todo) {
        todo.setId(null);
        Todo savedTodo = todoRepository.save(todo);
        eventPublisher.publishEvent(new TodoCreatedEvent(
                savedTodo.getId(),
                savedTodo.getTitle(),
                savedTodo.getDescription(),
                savedTodo.isCompleted(),
                savedTodo.getCreatedAt(),
                savedTodo.getUpdatedAt(),
                savedTodo.getVersion()
        ));
        return savedTodo;
    }

    public Todo update(Long id, Todo todo) {
        Todo existing = getById(id);
        existing.setTitle(todo.getTitle());
        existing.setDescription(todo.getDescription());
        existing.setCompleted(todo.isCompleted());
        return todoRepository.save(existing);
    }

    public void delete(Long id) {
        Todo existing = getById(id);
        todoRepository.delete(existing);
    }
}
