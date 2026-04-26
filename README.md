# Spring Boot Todo CRUD (Java 25)

## Stack

- Java 25
- Spring Boot 3.5.0
- Maven
- Spring Data JPA
- PostgreSQL (AWS RDS compatible)

## Run from Local CLI (Remote PostgreSQL / AWS RDS)

### Windows Local (PowerShell)

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://<rds-endpoint>:5432/<db-name>?sslmode=require"
$env:SPRING_DATASOURCE_USERNAME="<db-username>"
$env:SPRING_DATASOURCE_PASSWORD="<db-password>"
$env:SPRING_JPA_HIBERNATE_DDL_AUTO="update"
mvn spring-boot:run
```

### macOS Local (Terminal / zsh)

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://<rds-endpoint>:5432/<db-name>?sslmode=require"
export SPRING_DATASOURCE_USERNAME="<db-username>"
export SPRING_DATASOURCE_PASSWORD="<db-password>"
export SPRING_JPA_HIBERNATE_DDL_AUTO="update"
mvn spring-boot:run
```

## Run from IntelliJ (Remote PostgreSQL / AWS RDS)

1. Open this project in IntelliJ and wait for Maven import to finish.
2. Set Project SDK to Java 25:
   - `File` -> `Project Structure` -> `Project SDK` -> `JDK 25`
3. Create a Spring Boot run configuration:
   - `Run` -> `Edit Configurations` -> `+` -> `Spring Boot`
   - Main class: `com.example.todo.TodoApplication`
4. In the run configuration, set Environment variables:
   - `SPRING_DATASOURCE_URL=jdbc:postgresql://<rds-endpoint>:5432/<db-name>?sslmode=require`
   - `SPRING_DATASOURCE_USERNAME=<db-username>`
   - `SPRING_DATASOURCE_PASSWORD=<db-password>`
   - `SPRING_JPA_HIBERNATE_DDL_AUTO=update`
5. Click `Run`.

After startup, verify:
- `GET http://localhost:8080/api/todos`
- Data is persisted in your RDS database.

## API

Base URL: `http://localhost:8080`

Content type:
- Request: `application/json`
- Response: `application/json` (except `DELETE`, which returns empty body)

Todo object format:

```json
{
  "id": 1,
  "title": "Learn Spring Boot",
  "description": "Build Todo CRUD",
  "completed": false,
  "createdAt": "2026-04-26T21:40:00.123456",
  "updatedAt": "2026-04-26T21:40:00.123456",
  "version": 0
}
```

System-managed fields (read-only): `id`, `createdAt`, `updatedAt`, `version`

### 1) Create Todo

- Method/Path: `POST /api/todos`
- Success status: `201 Created`
- Request body:

```json
{
  "title": "Learn Spring Boot",
  "description": "Build Todo CRUD",
  "completed": false
}
```

- Success response body:

```json
{
  "id": 1,
  "title": "Learn Spring Boot",
  "description": "Build Todo CRUD",
  "completed": false,
  "createdAt": "2026-04-26T21:40:00.123456",
  "updatedAt": "2026-04-26T21:40:00.123456",
  "version": 0
}
```

### 2) List Todos

- Method/Path: `GET /api/todos`
- Success status: `200 OK`
- Success response body:

```json
[
  {
    "id": 1,
    "title": "Learn Spring Boot",
    "description": "Build Todo CRUD",
    "completed": false,
    "createdAt": "2026-04-26T21:40:00.123456",
    "updatedAt": "2026-04-26T21:40:00.123456",
    "version": 0
  },
  {
    "id": 2,
    "title": "Connect to AWS RDS",
    "description": "Use PostgreSQL datasource",
    "completed": true,
    "createdAt": "2026-04-26T21:41:10.012345",
    "updatedAt": "2026-04-26T21:42:45.765432",
    "version": 1
  }
]
```

### 3) Get Todo by ID

- Method/Path: `GET /api/todos/{id}`
- Success status: `200 OK`
- Success response body:

```json
{
  "id": 1,
  "title": "Learn Spring Boot",
  "description": "Build Todo CRUD",
  "completed": false,
  "createdAt": "2026-04-26T21:40:00.123456",
  "updatedAt": "2026-04-26T21:40:00.123456",
  "version": 0
}
```

- Not found status: `404 Not Found`
- Not found response body:

```json
{
  "message": "Todo not found with id 999"
}
```

### 4) Update Todo

- Method/Path: `PUT /api/todos/{id}`
- Success status: `200 OK`
- Request body:

```json
{
  "title": "Learn Spring Boot",
  "description": "CRUD completed",
  "completed": true
}
```

- Success response body:

```json
{
  "id": 1,
  "title": "Learn Spring Boot",
  "description": "CRUD completed",
  "completed": true,
  "createdAt": "2026-04-26T21:40:00.123456",
  "updatedAt": "2026-04-26T21:43:15.987654",
  "version": 1
}
```

### 5) Delete Todo

- Method/Path: `DELETE /api/todos/{id}`
- Success status: `204 No Content`
- Success response body: empty

### Validation Behavior

- `title` is required (`@NotBlank`).
- When validation fails, Spring Boot returns `400 Bad Request`.
- Concurrent update conflicts return `409 Conflict` with:

```json
{
  "message": "Todo was updated by another request. Please retry."
}
```

