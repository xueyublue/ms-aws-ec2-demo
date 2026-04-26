# Spring Boot Todo CRUD (Java 25)

## Stack
- Java 25
- Spring Boot 3.5.0
- Maven
- Spring Data JPA
- H2 in-memory database

## Run
```bash
mvn spring-boot:run
```

## API
- `GET /api/todos` - list todos
- `GET /api/todos/{id}` - get one todo
- `POST /api/todos` - create todo
- `PUT /api/todos/{id}` - update todo
- `DELETE /api/todos/{id}` - delete todo

Example create payload:
```json
{
  "title": "Learn Spring Boot",
  "description": "Build Todo CRUD",
  "completed": false
}
```