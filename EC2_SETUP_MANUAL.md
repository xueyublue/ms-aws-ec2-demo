# EC2 Setup Manual

This guide explains how to deploy this Spring Boot microservice to AWS EC2 with PostgreSQL (RDS) and SQS integration.

## 1) Prerequisites

- AWS account access with permissions to manage:
  - EC2
  - IAM
  - Security Groups
  - RDS
  - SQS
- Existing RDS PostgreSQL instance
- Existing SQS queue
- SSH key pair (`.pem`) for EC2 access

## 2) Create and Configure EC2

1. Launch an EC2 instance (Amazon Linux 2023 recommended).
   - Name tag example: `todo-ms-ec2-dev`
2. Instance type:
   - `t3.small` for typical dev/test
   - `t3.micro` for light usage
3. Create/attach a Security Group (example: `todo-ms-ec2-sg`) with inbound rules:
   - SSH `22` from your IP only
   - App port `8080` from your IP (or from ALB security group)
4. Ensure outbound internet access is enabled.

### 2.1 Find and Allow Your Public IP

Use your **public IPv4** (internet-facing IP), not local/private IP (for example `192.168.x.x`).

Get your public IP:

```powershell
(Invoke-WebRequest -UseBasicParsing https://checkip.amazonaws.com).Content.Trim()
```

or

```bash
curl https://checkip.amazonaws.com
```

If result is `203.0.113.25`, set Security Group source as:
- `203.0.113.25/32`

Apply this source to:
- SSH `22` (recommended always)
- Port `8080` (if directly exposing app during testing)

Note:
- If your ISP IP changes, update the Security Group rule again.
- If you are connected to VPN/corporate network, AWS sees your VPN/proxy public egress IP.

### 2.2 EC2 Key Pair (.pem) - Required for SSH/SCP

When launching EC2, you must select a Key pair:

1. In launch wizard, under **Key pair (login)**:
   - Choose **Create new key pair** (recommended for first-time setup).
   - Key pair name example: `todo-ec2-key`.
   - Key pair type: `RSA`.
   - Private key format: `.pem`.
2. AWS automatically downloads the private key file once (for example: `todo-ec2-key.pem`).
3. Save this file securely on your local machine. You will use it for:
   - SSH access
   - SCP file upload (Step 5)

Important notes:
- AWS allows private key download only once.
- If you lose the `.pem`, you cannot download it again from AWS.
- If lost, use one of these recovery options:
  - Use another key pair you still have access to.
  - Use AWS Systems Manager Session Manager.
  - Launch a new EC2 instance with a new key pair.

## 3) Configure IAM Role for EC2

Create a dedicated IAM role and attach it to your EC2 instance.

### 3.1 Create IAM Role (Console)

1. Go to **IAM** -> **Roles** -> **Create role**.
2. Trusted entity type: **AWS service**.
3. Use case: **EC2**.
4. Role name example: `todo-ms-ec2-role`.
5. Create role.

### 3.2 Create and Attach SQS Policy

1. Go to **IAM** -> **Policies** -> **Create policy**.
2. Switch to JSON and use a policy like this (replace region/account/queue):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "TodoMsSqsAccess",
      "Effect": "Allow",
      "Action": [
        "sqs:SendMessage",
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes"
      ],
      "Resource": "arn:aws:sqs:ap-southeast-2:<account-id>:<queue-name>"
    }
  ]
}
```

3. Policy name example: `todo-ms-sqs-policy`.
4. Open role `todo-ms-ec2-role` -> **Add permissions** -> attach `todo-ms-sqs-policy`.

### 3.3 (Optional) Add KMS Permissions

If your SQS queue uses KMS encryption, also add:
- `kms:Decrypt`
- `kms:GenerateDataKey`

Example extra statement:

```json
{
  "Sid": "TodoMsKmsAccess",
  "Effect": "Allow",
  "Action": [
    "kms:Decrypt",
    "kms:GenerateDataKey"
  ],
  "Resource": "arn:aws:kms:ap-southeast-2:<account-id>:key/<kms-key-id>"
}
```

### 3.4 Attach Role to EC2 Instance

1. Go to **EC2** -> **Instances** -> select your instance.
2. **Actions** -> **Security** -> **Modify IAM role**.
3. Select `todo-ms-ec2-role`.
4. Save.

### 3.5 Verify Role on EC2

SSH to EC2 and run:

```bash
aws sts get-caller-identity
```

Expected: an assumed-role ARN containing `todo-ms-ec2-role`.

Use IAM role authentication on EC2 (preferred) instead of storing AWS access keys on server.

### 3.6 (Windows GUI) Fix `.pem` Permission Error for SSH

If you see errors like `UNPROTECTED PRIVATE KEY FILE` or `bad permissions` when using SSH, fix file permissions using Windows GUI:

1. Locate your key file (for example: `C:\Users\<your-user>\Downloads\todo-ec2-key.pem`).
2. Right-click the `.pem` file -> **Properties**.
3. Open **Security** tab -> click **Advanced**.
4. Click **Disable inheritance**.
5. Choose **Remove all inherited permissions**.
6. Click **Add** -> **Select a principal**.
7. Enter your Windows username -> **Check Names** -> **OK**.
8. Grant only **Read** permission.
9. Remove other users/groups if present (`Users`, `Authenticated Users`, `Everyone`, etc.).
10. Click **Apply** -> **OK**.

Recommended:
- Move key to `C:\Users\<your-user>\.ssh\todo-ec2-key.pem`
- Apply the same permission settings there.

Retry SSH:

```bash
ssh -i C:\Users\<your-user>\.ssh\todo-ec2-key.pem ec2-user@<ec2-public-ip>
```

## 4) Install Java on EC2

Connect to EC2:

```bash
ssh -i <your-key.pem> ec2-user@<ec2-public-ip>
```

Install Java 25:

```bash
sudo dnf update -y
sudo dnf install -y java-25-amazon-corretto-headless
java -version
```

## 5) Build and Upload the App

Build from local machine (or CI):

```bash
mvn clean package -DskipTests
```

Copy jar to EC2:

```bash
scp -i <your-key.pem> target/todo-0.0.1-SNAPSHOT.jar ec2-user@<ec2-public-ip>:/home/ec2-user/app.jar
```

## 6) Configure Environment Variables on EC2

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
APP_SQS_CONSUMER_POLL_DELAY_MS=5000
APP_SQS_CONSUMER_MAX_MESSAGES=10
APP_SQS_CONSUMER_WAIT_TIME_SECONDS=10
EOF
```

## 7) Run as systemd Service

Create service file:

```bash
sudo tee /etc/systemd/system/todo.service > /dev/null <<'EOF'
[Unit]
Description=Todo Spring Boot Service
After=network.target

[Service]
User=ec2-user
WorkingDirectory=/home/ec2-user
EnvironmentFile=/home/ec2-user/todo.env
ExecStart=/usr/bin/java -jar /home/ec2-user/app.jar
SuccessExitStatus=143
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
```

Enable and start:

```bash
sudo systemctl daemon-reload
sudo systemctl enable todo
sudo systemctl start todo
sudo systemctl status todo
```

View logs:

```bash
journalctl -u todo -f
```

## 8) Network and Connectivity Checks

- EC2 security group allows app port (`8080`) from expected source.
- RDS security group allows `5432` inbound from EC2 security group.
- EC2 can access SQS endpoint in the configured region.

## 9) Validation Checklist

1. API check:

```bash
curl http://<ec2-public-ip>:8080/api/todos
```

2. Create a Todo via `POST /api/todos`.
3. Confirm:
   - Todo row is persisted in `todo` table.
   - SQS message is published.
   - Consumer stores message in `sqs_message_log`.
   - Message is removed from SQS queue after processing.

## 10) Common Troubleshooting

- **SQS queue URL appears empty**
  - Ensure env variable name is exactly: `APP_SQS_TODO_CREATED_QUEUE_URL`.
- **AccessDenied for SQS**
  - Update IAM policy to include required SQS actions.
- **Cannot connect to RDS**
  - Verify RDS endpoint/credentials and security group rules.
- **App not starting on boot**
  - Check `sudo systemctl status todo` and `journalctl -u todo -f`.

## 11) Production Hardening (Recommended)

- Put service behind an ALB + HTTPS (ACM certificate).
- Store secrets in AWS Secrets Manager or SSM Parameter Store.
- Ship logs to CloudWatch.
- Use CI/CD pipeline for build and deployment.

## 12) How to Shut Down (App and EC2)

### 12.1 Stop Only the Application Service (Keep EC2 Running)

Use this when you want to stop the Spring Boot app but keep the server online:

```bash
sudo systemctl stop todo
sudo systemctl status todo
```

To start again:

```bash
sudo systemctl start todo
```

### 12.2 Restart the Application Service

```bash
sudo systemctl restart todo
sudo systemctl status todo
```

### 12.3 Shut Down the EC2 Instance (AWS Console)

1. Open **EC2** -> **Instances**.
2. Select your instance (for example: `todo-ms-ec2-dev`).
3. Click **Instance state** -> **Stop instance**.
4. Confirm stop.

### 12.4 Shut Down the EC2 Instance (CLI)

```bash
aws ec2 stop-instances --instance-ids <instance-id>
```

Check status:

```bash
aws ec2 describe-instances --instance-ids <instance-id> --query "Reservations[0].Instances[0].State.Name" --output text
```

### 12.5 Important Notes

- **Stop** keeps EBS volumes/data, but public IP may change unless you use Elastic IP.
- **Terminate** permanently deletes the instance (and may delete root volume depending on settings).
- Stopped instance still incurs storage cost (EBS), but compute cost stops.
