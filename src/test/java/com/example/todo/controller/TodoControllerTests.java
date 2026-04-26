package com.example.todo.controller;

import com.example.todo.exception.TodoNotFoundException;
import com.example.todo.model.Todo;
import com.example.todo.service.TodoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TodoController.class)
class TodoControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TodoService todoService;

    @Test
    void getAllShouldReturnTodos() throws Exception {
        Todo todo = new Todo();
        todo.setId(1L);
        todo.setTitle("Task A");
        todo.setDescription("desc");
        todo.setCompleted(false);
        when(todoService.getAll()).thenReturn(List.of(todo));

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Task A"));
    }

    @Test
    void createShouldReturnCreatedTodo() throws Exception {
        Todo created = new Todo();
        created.setId(1L);
        created.setTitle("Created task");
        created.setDescription("desc");
        created.setCompleted(false);
        when(todoService.create(any(Todo.class))).thenReturn(created);

        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Created task",
                                  "description": "desc",
                                  "completed": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Created task"));
    }

    @Test
    void createShouldReturnBadRequestWhenTitleIsBlank() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "description": "desc",
                                  "completed": false
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(todoService, times(0)).create(any(Todo.class));
    }

    @Test
    void getByIdShouldReturnNotFoundWhenMissing() throws Exception {
        when(todoService.getById(999L)).thenThrow(new TodoNotFoundException(999L));

        mockMvc.perform(get("/api/todos/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Todo not found with id 999"));
    }

    @Test
    void updateShouldReturnConflictOnOptimisticLockFailure() throws Exception {
        when(todoService.update(eq(1L), any(Todo.class)))
                .thenThrow(new OptimisticLockingFailureException("conflict"));

        mockMvc.perform(put("/api/todos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated title",
                                  "description": "Updated desc",
                                  "completed": true
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Todo was updated by another request. Please retry."));
    }

    @Test
    void deleteShouldReturnNoContent() throws Exception {
        doNothing().when(todoService).delete(1L);

        mockMvc.perform(delete("/api/todos/1"))
                .andExpect(status().isNoContent());

        verify(todoService).delete(1L);
    }

    @Test
    void deleteShouldReturnNotFoundWhenMissing() throws Exception {
        doThrow(new TodoNotFoundException(11L)).when(todoService).delete(11L);

        mockMvc.perform(delete("/api/todos/11"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Todo not found with id 11"));
    }
}
