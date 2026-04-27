# Spring Boot Todo CRUD (Java 25)

## Stack

- Java 25
- Spring Boot 3.5.0
- Maven
- Spring Data JPA
- PostgreSQL (AWS RDS compatible)
- AWS SQS publish + consume (optional)

## High-Level Architecture Flow

```text
Client
  -> POST /api/todos
  -> Todo API (Spring Boot)
  -> Save Todo to PostgreSQL (RDS)
  -> Publish TODO_CREATED event to SQS
  -> SQS Consumer polls queue
  -> Save consumed message details to PostgreSQL table (sqs_message_log)
```

Flow summary:
1. API receives Todo create request.
2. Todo record is persisted in the `todo` table.
3. App publishes a `TODO_CREATED` message to SQS (when publisher is enabled).
4. Consumer polls SQS and reads messages (when consumer is enabled).
5. Consumer stores message metadata/body into `sqs_message_log`.
6. Consumer deletes processed message from SQS queue.

## Run from Local CLI (Remote PostgreSQL / AWS RDS)

### Windows Local (PowerShell)

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://<rds-endpoint>:5432/<db-name>?sslmode=require"
$env:SPRING_DATASOURCE_USERNAME="<db-username>"
$env:SPRING_DATASOURCE_PASSWORD="<db-password>"
$env:SPRING_JPA_HIBERNATE_DDL_AUTO="update"
$env:APP_SQS_ENABLED="false"
# Optional only when APP_SQS_ENABLED=true:
# $env:APP_SQS_REGION="ap-southeast-1"
# $env:APP_SQS_TODO_CREATED_QUEUE_URL="<sqs-queue-url>"
# $env:APP_SQS_CONSUMER_ENABLED="true"
# $env:APP_SQS_CONSUMER_QUEUE_URL="<sqs-queue-url>"
mvn spring-boot:run
```

## AWS Credentials Setup (Local)

Before using SQS features locally, configure AWS credentials on your machine.

1. In AWS Console, create a new IAM user for programmatic access (for example: `todo-app-local`).
2. Attach required permissions (least privilege):
   - For publish: `sqs:SendMessage`
   - For consume: `sqs:ReceiveMessage`, `sqs:DeleteMessage`, `sqs:GetQueueAttributes`
3. Create access keys for this IAM user and download/copy:
   - `AWS Access Key ID`
   - `AWS Secret Access Key`
4. On your local machine, run:

```bash
aws configure
```

5. Provide values when prompted:
   - `AWS Access Key ID`
   - `AWS Secret Access Key`
   - `Default region name` (for example: `ap-southeast-2`)
   - `Default output format` (for example: `json`)

Optional verification:

```bash
aws sts get-caller-identity
```

### macOS Local (Terminal / zsh)

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://<rds-endpoint>:5432/<db-name>?sslmode=require"
export SPRING_DATASOURCE_USERNAME="<db-username>"
export SPRING_DATASOURCE_PASSWORD="<db-password>"
export SPRING_JPA_HIBERNATE_DDL_AUTO="update"
export APP_SQS_ENABLED="false"
# Optional only when APP_SQS_ENABLED=true:
# export APP_SQS_REGION="ap-southeast-1"
# export APP_SQS_TODO_CREATED_QUEUE_URL="<sqs-queue-url>"
# export APP_SQS_CONSUMER_ENABLED="true"
# export APP_SQS_CONSUMER_QUEUE_URL="<sqs-queue-url>"
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
   - `APP_SQS_ENABLED=false` (set to `true` to enable SQS publish)
   - `APP_SQS_REGION=ap-southeast-1` (required only when SQS is enabled)
   - `APP_SQS_TODO_CREATED_QUEUE_URL=<sqs-queue-url>` (required only when SQS is enabled)
   - `APP_SQS_CONSUMER_ENABLED=false` (set to `true` to enable SQS consumer polling)
   - `APP_SQS_CONSUMER_QUEUE_URL=<sqs-queue-url>` (optional; defaults to `APP_SQS_TODO_CREATED_QUEUE_URL`)
5. Click `Run`.

After startup, verify:
- `GET http://localhost:8080/api/todos`
- Data is persisted in your RDS database.
- If SQS publishing is enabled, creating a Todo sends a `TODO_CREATED` message.
- If SQS consumer is enabled, consumed messages are saved to `sqs_message_log`.

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

## Todo Created Event (SQS)

When a new Todo is created successfully, the app sends a `TODO_CREATED` message to SQS after transaction commit.

Example message body:

```json
{
  "eventType": "TODO_CREATED",
  "sentAt": "2026-04-26T22:05:23.123456",
  "todo": {
    "id": 1,
    "title": "Learn Spring Boot",
    "description": "Build Todo CRUD",
    "completed": false,
    "createdAt": "2026-04-26T22:05:23.000001",
    "updatedAt": "2026-04-26T22:05:23.000001",
    "version": 0
  }
}
```

Required environment variables for SQS publishing:
- `APP_SQS_ENABLED=true`
- `APP_SQS_REGION=<aws-region>`
- `APP_SQS_TODO_CREATED_QUEUE_URL=<sqs-queue-url>`

If SQS is disabled (`APP_SQS_ENABLED=false`), Todo creation still works and no message is sent.

AWS permissions required for publishing:
- `sqs:SendMessage` on the configured queue

## SQS Consumer and Message Log Table

When SQS consumer is enabled, the app polls SQS and stores each consumed message in DB table `sqs_message_log`, then deletes it from the queue.

Consumer environment variables:
- `APP_SQS_CONSUMER_ENABLED=true`
- `APP_SQS_CONSUMER_QUEUE_URL=<sqs-queue-url>` (optional; defaults to `APP_SQS_TODO_CREATED_QUEUE_URL`)
- `APP_SQS_CONSUMER_POLL_DELAY_MS=5000` (optional)
- `APP_SQS_CONSUMER_MAX_MESSAGES=10` (optional)
- `APP_SQS_CONSUMER_WAIT_TIME_SECONDS=10` (optional)

`sqs_message_log` includes:
- `message_id` (unique SQS message id)
- `queue_url`
- `body`
- `attributes_json`
- `message_attributes_json`
- `received_at`

AWS permissions required for consuming:
- `sqs:ReceiveMessage`
- `sqs:DeleteMessage`
- `sqs:GetQueueAttributes`

## Testing

Run all tests:

```bash
mvn test
```

Current test coverage includes:
- Repository tests (`TodoRepositoryTests`)
- Service tests (`TodoServiceTests`)
- Controller tests (`TodoControllerTests`)
- SQS consumer tests (`SqsMessageConsumerTests`)
- Application context test (`TodoApplicationTests`)

