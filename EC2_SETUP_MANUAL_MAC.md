# EC2 Setup Manual (macOS)

This version is optimized for macOS local development/deployment steps.

## Related Manuals

- Project/API usage: `README.md`
- CI/CD guide (macOS): `CICD_SETUP_MANUAL_MAC.md`
- CI/CD guide (Windows): `CICD_SETUP_MANUAL_WINDOWS.md`

## 1) Prerequisites

- AWS account with permissions for EC2, IAM, Security Groups, RDS, SQS
- Existing RDS PostgreSQL + SQS queue
- macOS Terminal (zsh/bash)
- `ssh`/`scp`, Maven, JDK 25 installed locally

## 2) Launch and Secure EC2

1. Launch Amazon Linux 2023 EC2.
2. Attach Security Group:
  - SSH `22` from your public IP only
  - App `8080` from your public IP only (or ALB SG)
3. Keep outbound internet enabled.

Find public IP:

```bash
curl https://checkip.amazonaws.com
```

Use CIDR in SG (example): `203.0.113.25/32`.

## 3) EC2 Key Pair and Permission

1. During EC2 launch, create/select key pair (`.pem`).
2. Save to `~/.ssh/todo-ec2-key.pem`.
3. Set permission:

```bash
chmod 400 ~/.ssh/todo-ec2-key.pem
```

Test SSH:

```bash
ssh -i ~/.ssh/todo-ec2-key.pem ec2-user@<ec2-public-ip>
```

## 4) Configure IAM Role on EC2

1. Create role `todo-ms-ec2-role` (trusted entity: EC2).
2. Create and attach SQS policy with:
  - `sqs:SendMessage`
  - `sqs:ReceiveMessage`
  - `sqs:DeleteMessage`
  - `sqs:GetQueueAttributes`
3. Attach role to EC2 instance.

Verify:

```bash
aws sts get-caller-identity
```

## 5) Install Java on EC2

```bash
sudo dnf update -y
sudo dnf install -y java-25-amazon-corretto-headless
java -version
```

## 6) Build and Upload App from macOS

Build locally:

```bash
mvn clean package -DskipTests
```

Create release folders on EC2:

```bash
ssh -i ~/.ssh/todo-ec2-key.pem ec2-user@<ec2-public-ip> "mkdir -p /home/ec2-user/app/releases /home/ec2-user/app/current"
```

Upload and update symlink:

```bash
scp -i ~/.ssh/todo-ec2-key.pem target/todo-0.0.1-SNAPSHOT.jar ec2-user@<ec2-public-ip>:/home/ec2-user/app/releases/app-manual.jar
ssh -i ~/.ssh/todo-ec2-key.pem ec2-user@<ec2-public-ip> "ln -sfn /home/ec2-user/app/releases/app-manual.jar /home/ec2-user/app/current/app.jar"
```

## 7) Configure App Env and systemd on EC2

Create env file:

```bash
cat <<'EOF' > /home/ec2-user/todo.env
SPRING_DATASOURCE_URL=jdbc:postgresql://<rds-endpoint>:5432/<db-name>?sslmode=require
SPRING_DATASOURCE_USERNAME=<db-username>
SPRING_DATASOURCE_PASSWORD=<db-password>
SPRING_JPA_HIBERNATE_DDL_AUTO=update
APP_SQS_ENABLED=true
APP_SQS_REGION=ap-southeast-2
APP_SQS_TODO_CREATED_QUEUE_URL=https://sqs.ap-southeast-2.amazonaws.com/<account-id>/<queue-name>
APP_SQS_CONSUMER_ENABLED=true
APP_SQS_CONSUMER_QUEUE_URL=https://sqs.ap-southeast-2.amazonaws.com/<account-id>/<queue-name>
EOF
```

Create service:

```bash
sudo tee /etc/systemd/system/todo.service > /dev/null <<'EOF'
[Unit]
Description=Todo Spring Boot Service
After=network.target

[Service]
User=ec2-user
WorkingDirectory=/home/ec2-user/app
EnvironmentFile=/home/ec2-user/todo.env
ExecStart=/usr/bin/java -jar /home/ec2-user/app/current/app.jar
SuccessExitStatus=143
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
```

Start:

```bash
sudo systemctl daemon-reload
sudo systemctl enable todo
sudo systemctl restart todo
sudo systemctl status todo
```

## 8) Validate

```bash
curl http://<ec2-public-ip>:8080/api/todos
journalctl -u todo -n 100 --no-pager
```

## 9) Shutdown

```bash
sudo systemctl stop todo
aws ec2 stop-instances --instance-ids <instance-id>
```

