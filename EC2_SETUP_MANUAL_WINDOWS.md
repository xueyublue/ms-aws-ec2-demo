# EC2 Setup Manual (Windows)

This version is optimized for Windows local development/deployment steps.

## Related Manuals

- Project/API usage: `README.md`
- CI/CD guide (Windows + macOS): `CICD_SETUP_MANUAL.md`

## 1) Prerequisites

- AWS account with permissions for EC2, IAM, Security Groups, RDS, SQS
- Existing RDS PostgreSQL + SQS queue
- Windows PowerShell
- OpenSSH client (`ssh`, `scp`) available on Windows
- Maven + JDK 25 installed locally

## 2) Launch and Secure EC2

1. Launch Amazon Linux 2023 EC2.
2. Attach Security Group:
  - SSH `22` from your public IP only
  - App `8080` from your public IP only (or ALB SG)
3. Keep outbound internet enabled.

Find public IP (PowerShell):

```powershell
(Invoke-WebRequest -UseBasicParsing https://checkip.amazonaws.com).Content.Trim()
```

Use CIDR format in SG (example): `203.0.113.25/32`.

## 3) EC2 Key Pair and Windows Permission Fix

1. During EC2 launch, create/select key pair (`.pem`).
2. Save it to `C:\Users\<you>\.ssh\todo-ec2-key.pem`.
3. If SSH shows key permission warnings, fix file permissions via File Properties -> Security -> Advanced:
  - Disable inheritance
  - Remove inherited permissions
  - Add only your user with Read

Test SSH:

```powershell
ssh -i "C:\Users\<you>\.ssh\todo-ec2-key.pem" ec2-user@<ec2-public-ip>
```

## 4) Configure IAM Role on EC2

1. Create role `todo-ms-ec2-role` (trusted entity: EC2).
2. Create and attach policy for SQS:
  - `sqs:SendMessage`
  - `sqs:ReceiveMessage`
  - `sqs:DeleteMessage`
  - `sqs:GetQueueAttributes`
3. Create and attach policy for Parameter Store DB config:
  - `ssm:GetParameter`
  - `ssm:GetParameters`
  - Resource scope example:
    - `arn:aws:ssm:ap-southeast-1:715840489161:parameter/todo/prod/db/url`
    - `arn:aws:ssm:ap-southeast-1:715840489161:parameter/todo/prod/db/username`
    - `arn:aws:ssm:ap-southeast-1:715840489161:parameter/todo/prod/db/password`
4. Add KMS decrypt permission for SecureString password:
  - `kms:Decrypt`
  - KMS key used by `/todo/prod/db/password`
5. Attach role to EC2 instance.

Verify on EC2:

```bash
aws sts get-caller-identity
aws ssm get-parameter --name "/todo/prod/db/url" --region ap-southeast-1
aws ssm get-parameter --name "/todo/prod/db/password" --with-decryption --region ap-southeast-1
```

## 5) Install Java on EC2

```bash
sudo dnf update -y
sudo dnf install -y java-25-amazon-corretto-headless
java -version
```

## 6) Build and Upload App from Windows

Build locally:

```powershell
mvn clean package -DskipTests
```

Create release folders on EC2:

```powershell
ssh -i "C:\Users\<you>\.ssh\todo-ec2-key.pem" ec2-user@<ec2-public-ip> "mkdir -p /home/ec2-user/app/releases /home/ec2-user/app/current"
```

Upload and update symlink:

```powershell
scp -i "C:\Users\<you>\.ssh\todo-ec2-key.pem" "target/todo-0.0.1-SNAPSHOT.jar" ec2-user@<ec2-public-ip>:/home/ec2-user/app/releases/app-manual.jar
ssh -i "C:\Users\<you>\.ssh\todo-ec2-key.pem" ec2-user@<ec2-public-ip> "ln -sfn /home/ec2-user/app/releases/app-manual.jar /home/ec2-user/app/current/app.jar"
```

## 7) Configure Parameter Store + systemd on EC2

Create SSM parameters (run once from local or CloudShell):

```bash
aws ssm put-parameter --name "/todo/prod/db/url" --type String --value "jdbc:postgresql://<rds-endpoint>:5432/<db-name>?sslmode=require" --overwrite --region ap-southeast-1
aws ssm put-parameter --name "/todo/prod/db/username" --type String --value "<db-username>" --overwrite --region ap-southeast-1
aws ssm put-parameter --name "/todo/prod/db/password" --type SecureString --value "<db-password>" --overwrite --region ap-southeast-1
```

Create loader script on EC2 (`/home/ec2-user/app/bin/load-env-from-ssm.sh`):

```bash
mkdir -p /home/ec2-user/app/bin
cat <<'EOF' > /home/ec2-user/app/bin/load-env-from-ssm.sh
#!/usr/bin/env bash
set -euo pipefail

REGION="ap-southeast-1"
ENV_FILE="/home/ec2-user/todo.env"

DB_URL=$(aws ssm get-parameter --region "$REGION" --name "/todo/prod/db/url" --query "Parameter.Value" --output text)
DB_USER=$(aws ssm get-parameter --region "$REGION" --name "/todo/prod/db/username" --query "Parameter.Value" --output text)
DB_PASS=$(aws ssm get-parameter --region "$REGION" --name "/todo/prod/db/password" --with-decryption --query "Parameter.Value" --output text)

cat > "$ENV_FILE" <<EOT
SPRING_DATASOURCE_URL=$DB_URL
SPRING_DATASOURCE_USERNAME=$DB_USER
SPRING_DATASOURCE_PASSWORD=$DB_PASS
SPRING_JPA_HIBERNATE_DDL_AUTO=update
APP_SQS_ENABLED=true
APP_SQS_REGION=ap-southeast-1
APP_SQS_TODO_CREATED_QUEUE_URL=https://sqs.ap-southeast-1.amazonaws.com/715840489161/<queue-name>
APP_SQS_CONSUMER_ENABLED=true
APP_SQS_CONSUMER_QUEUE_URL=https://sqs.ap-southeast-1.amazonaws.com/715840489161/<queue-name>
EOT

chmod 600 "$ENV_FILE"
EOF
chmod +x /home/ec2-user/app/bin/load-env-from-ssm.sh
```

Test loader script:

```bash
/home/ec2-user/app/bin/load-env-from-ssm.sh
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
ExecStartPre=/home/ec2-user/app/bin/load-env-from-ssm.sh
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
journalctl -u todo -f
```

If startup fails, check common issues in logs:
- `AccessDeniedException`: missing `ssm:GetParameter` or `kms:Decrypt`
- `ParameterNotFound`: wrong parameter name/path
- region mismatch between script and parameter location

## 9) Shutdown

```bash
sudo systemctl stop todo
aws ec2 stop-instances --instance-ids <instance-id>
```

