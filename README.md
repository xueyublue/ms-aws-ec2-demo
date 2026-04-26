# Spring Boot Todo CRUD (Java 25)

## Stack

- Java 25
- Spring Boot 3.5.0
- Maven
- Spring Data JPA
- PostgreSQL (AWS RDS compatible)

## Run

```bash
mvn spring-boot:run
```

Set DB connection variables before running:

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://<rds-endpoint>:5432/<db-name>?sslmode=require"
export SPRING_DATASOURCE_USERNAME="<db-username>"
export SPRING_DATASOURCE_PASSWORD="<db-password>"
```

(On PowerShell, use `$env:SPRING_DATASOURCE_URL="..."` format.)

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

