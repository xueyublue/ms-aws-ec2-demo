# CI/CD Setup Manual (GitHub -> AWS EC2)

This guide explains how to set up a production-style CI/CD pipeline for this project using:

-   GitHub Actions
-   Maven build/test
-   SSH deploy to EC2
-   `systemd` service restart (`todo`)

## Related Manuals

-   Project setup and runtime config: `README.md`
-   EC2 infrastructure setup guide: `EC2_SETUP_MANUAL.md`

---

## 1) Target Deployment Flow

```text
Developer push/merge to main
  -> GitHub Actions workflow starts
  -> Build + test with Maven (JDK 25)
  -> Copy new jar to EC2
  -> Restart systemd service (todo)
  -> Verify service is active
```

---

## 2) Prerequisites

-   GitHub repository for this project
-   Running EC2 instance with app already deployed once
-   `todo` systemd service already configured on EC2
-   SSH access to EC2 confirmed
-   Java 25 runtime installed on EC2
-   Security group allows:
    -   SSH `22` from GitHub Actions runners (or temporarily broad access until tightened with alternatives)

> Note: GitHub Actions runners use dynamic IP ranges. If strict ingress is required, prefer SSM deployment or a self-hosted runner in your VPC.

---

## 3) Prepare EC2 for Automated Deploy

### 3.1 Create stable app directory

SSH into EC2 and run:

```bash
mkdir -p /home/ec2-user/app/releases
mkdir -p /home/ec2-user/app/current
```

Recommended convention:

-   Release jars: `/home/ec2-user/app/releases/app-<timestamp>.jar`
-   Active jar symlink: `/home/ec2-user/app/current/app.jar`

### 3.2 Ensure systemd points to stable symlink path (step-by-step)

Goal: make sure `todo.service` always starts the stable symlink path:

-   `/home/ec2-user/app/current/app.jar`

#### 3.2.1 Check current service file

```bash
sudo systemctl cat todo
```

Look for the `ExecStart=` line.

#### 3.2.2 Edit the service file

You can use any of these methods.

Option A (easy): `nano`

```bash
sudo nano /etc/systemd/system/todo.service
```

Find/update line:

```ini
ExecStart=/usr/bin/java -jar /home/ec2-user/app/current/app.jar
```

Save and exit:

-   Press `Ctrl + O`, Enter, then `Ctrl + X`.

#### 3.2.3 Reload systemd and verify

Reload and verify:

```bash
sudo systemctl daemon-reload
sudo systemctl restart todo
sudo systemctl status todo
```

Optional quick check:

```bash
sudo systemctl cat todo | grep ExecStart
```

Expected:

```text
ExecStart=/usr/bin/java -jar /home/ec2-user/app/current/app.jar
```

### 3.3 Allow deploy user to restart service without password

Create sudoers entry:

```bash
sudo tee /etc/sudoers.d/todo-deploy > /dev/null <<'EOF'
ec2-user ALL=(ALL) NOPASSWD: /bin/systemctl restart todo, /bin/systemctl is-active todo
EOF
sudo chmod 440 /etc/sudoers.d/todo-deploy
```

Validate:

```bash
sudo -l
```

---

## 4) Create Dedicated Deploy SSH Key Pair

Use a dedicated key for CI/CD (do not reuse personal SSH key).

On local machine:

```bash
ssh-keygen -t ed25519 -C "github-actions-deploy" -f github_actions_deploy_key
```

This creates:

-   `github_actions_deploy_key` (private key)
-   `github_actions_deploy_key.pub` (public key)

Add public key to EC2:

```bash
cat github_actions_deploy_key.pub >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

Verify from local:

```bash
ssh -i github_actions_deploy_key ec2-user@<ec2-public-ip>
```

---

## 5) Configure GitHub Secrets

In GitHub repo: **Settings -> Secrets and variables -> Actions -> New repository secret**

Create these secrets:

-   `EC2_HOST`: EC2 public IP or DNS
-   `EC2_USER`: `ec2-user`
-   `EC2_SSH_KEY`: full content of private key (`github_actions_deploy_key`)
-   `EC2_APP_DIR`: `/home/ec2-user/app`

Optional:

-   `APP_HEALTHCHECK_URL`: `http://<ec2-public-ip>:8080/api/todos`

> Keep secrets masked and never commit private keys in repo.

---

## 6) Create GitHub Actions Workflow

Create file: `.github/workflows/deploy-ec2.yml`

```yaml
name: Build and Deploy to EC2

on:
  push:
    branches: [ "main" ]
  workflow_dispatch:

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    timeout-minutes: 20

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
          cache: maven

      - name: Build and test
        run: mvn -B clean test package

      - name: Resolve artifact path
        id: artifact
        run: |
          JAR_FILE=$(ls target/*.jar | head -n 1)
          echo "jar_file=$JAR_FILE" >> "$GITHUB_OUTPUT"
          echo "release_name=app-$(date +%Y%m%d%H%M%S).jar" >> "$GITHUB_OUTPUT"

      - name: Setup SSH
        run: |
          mkdir -p ~/.ssh
          echo "${{ secrets.EC2_SSH_KEY }}" > ~/.ssh/id_ed25519
          chmod 600 ~/.ssh/id_ed25519
          ssh-keyscan -H "${{ secrets.EC2_HOST }}" >> ~/.ssh/known_hosts

      - name: Upload release jar
        run: |
          scp -i ~/.ssh/id_ed25519 "${{ steps.artifact.outputs.jar_file }}" 
            "${{ secrets.EC2_USER }}@${{ secrets.EC2_HOST }}:${{ secrets.EC2_APP_DIR }}/releases/${{ steps.artifact.outputs.release_name }}"

      - name: Switch symlink and restart service
        run: |
          ssh -i ~/.ssh/id_ed25519 "${{ secrets.EC2_USER }}@${{ secrets.EC2_HOST }}" "
            ln -sfn '${{ secrets.EC2_APP_DIR }}/releases/${{ steps.artifact.outputs.release_name }}' '${{ secrets.EC2_APP_DIR }}/current/app.jar' &&
            sudo systemctl restart todo &&
            sudo systemctl is-active --quiet todo
          "

      - name: Optional health check
        if: ${{ secrets.APP_HEALTHCHECK_URL != '' }}
        run: |
          curl --fail --retry 10 --retry-delay 3 "${{ secrets.APP_HEALTHCHECK_URL }}"
```

---

## 7) Branch Protection and Release Safety

Set these GitHub controls so code cannot be merged/deployed without validation.

### 7.1 Open branch protection settings

1. In GitHub, open your repository.
2. Click **Settings** tab.
3. In the left menu, click **Rules**.
4. Click **Rulesets**.
5. Click **New ruleset** -> **New branch ruleset**.

### 7.2 Create a ruleset for `main`

1. Ruleset name: `protect-main`.
2. Under **Enforcement status**, choose **Active**.
3. Under **Targets**, set:
   - **Target**: `Branch`
   - **Branch targeting criteria**: `Include default branch` (or explicitly `main`)
4. Under **Branch protections**, enable:
   - **Require a pull request before merging**
   - **Require status checks to pass**
   - **Block force pushes**
   - **Block deletions**

### 7.3 Configure pull request requirements

Inside **Require a pull request before merging**:

1. Enable **Require approvals**.
2. Set minimum reviewers to at least `1`.
3. (Recommended) Enable **Dismiss stale pull request approvals when new commits are pushed**.
4. (Recommended) Enable **Require review from code owners** if your repo uses `CODEOWNERS`.

### 7.4 Configure required CI checks

Inside **Require status checks to pass**:

1. Enable **Require branches to be up to date before merging**.
2. Add required check(s) from your workflow run list.
3. Select your deploy workflow job check (for example `build-and-deploy`).
4. Save the ruleset.

Tips:

- If no checks appear, run the workflow once (`Actions` -> run `Build and Deploy to EC2`) and return to add it.
- Keep build/test check required; keep production deploy check required only if `main` directly represents production-ready code.

### 7.5 Optional manual approval gate with Environment

Use this when you want human approval before deployment steps run.

1. Go to **Settings** -> **Environments**.
2. Click **New environment**.
3. Name it `production`.
4. Under **Protection rules**, add **Required reviewers** (at least 1 person/team).
5. Save environment.
6. In `.github/workflows/deploy-ec2.yml`, set the job environment:

```yaml
jobs:
  build-and-deploy:
    environment: production
```

7. Commit the workflow change.
8. On next run, GitHub pauses the job for reviewer approval before continuing.

### 7.6 Verify protection is working

1. Create a test branch and open a PR to `main`.
2. Confirm merge is blocked until required checks complete.
3. Confirm merge is blocked until required approvals are added.
4. If environment protection is enabled, confirm workflow shows **Waiting for approval** before deploy steps.

---

## 8) Test the Workflow End-to-End

Use this test once your secrets and workflow file are ready.

### 8.1 Create a safe test change

1. Create a new branch from `main`:

```bash
git checkout main
git pull
git checkout -b test/cicd-pipeline
```

2. Make a harmless change (for example, add one line in `README.md`).
3. Commit and push:

```bash
git add README.md
git commit -m "test: verify CI/CD pipeline"
git push -u origin test/cicd-pipeline
```

### 8.2 Trigger the workflow

Choose one method:

1. Open a PR from `test/cicd-pipeline` to `main` and merge (if deploy runs on `main` pushes).
2. Or manually trigger from GitHub:
   - Go to **Actions**.
   - Open **Build and Deploy to EC2** workflow.
   - Click **Run workflow**.
   - Select branch (usually `main`), then click **Run workflow**.

### 8.3 Validate each workflow step in GitHub

1. Open the workflow run.
2. Confirm these steps are green:
   - **Checkout**
   - **Set up JDK 25**
   - **Build and test**
   - **Resolve artifact path**
   - **Setup SSH**
   - **Upload release jar**
   - **Switch symlink and restart service**
   - **Optional health check** (if configured)
3. If you enabled Environment approval, approve the pending deployment when prompted.

### 8.4 Verify deployment on EC2

SSH to EC2 and run:

```bash
ls -lt /home/ec2-user/app/releases | head
readlink -f /home/ec2-user/app/current/app.jar
sudo systemctl status todo --no-pager
journalctl -u todo -n 100 --no-pager
```

Check:

- A new timestamped jar exists under `/home/ec2-user/app/releases`.
- `current/app.jar` points to the latest uploaded release.
- `todo` service is `active (running)`.
- Logs show normal startup without fatal errors.

### 8.5 Verify application endpoint

From local machine (or EC2):

```bash
curl http://<ec2-public-ip>:8080/api/todos
```

Expected:

- You get HTTP `200` with JSON response.
- If health check URL is configured, the workflow should already have passed this.

---

## 9) Rollback Procedure

Since releases are uploaded with timestamps, rollback is easy.

On EC2:

```bash
ls -lt /home/ec2-user/app/releases
ln -sfn /home/ec2-user/app/releases/<previous-release>.jar /home/ec2-user/app/current/app.jar
sudo systemctl restart todo
sudo systemctl status todo
```

---

## 10) Validation Checklist

After pipeline run:

1.  GitHub Actions job status is green.
2.  EC2 service is active:

```bash
sudo systemctl status todo
```

1.  App endpoint works:

```bash
curl http://<ec2-public-ip>:8080/api/todos
```

1.  App logs show normal startup:

```bash
journalctl -u todo -n 200 --no-pager
```

---

## 11) Troubleshooting

### SSH permission denied

-   Check `EC2_SSH_KEY` secret content is correct and complete.
-   Ensure public key is in `~/.ssh/authorized_keys` for `ec2-user`.
-   Confirm EC2 Security Group allows SSH from required source.

### systemctl restart fails in workflow

-   Validate sudoers file permits `systemctl restart todo`.
-   Check service file path and app jar path.

### Workflow cannot find jar

-   Ensure Maven package step runs successfully.
-   Check output of `ls target/*.jar`.

### Service starts but app not healthy

-   Check env file values on EC2 (`todo.env`).
-   Check RDS connectivity and SQS IAM permissions.
-   Inspect logs: `journalctl -u todo -f`.

---

## 12) Security Best Practices

-   Use dedicated deploy key pair for CI/CD.
-   Rotate deploy keys regularly.
-   Use IAM role on EC2 for AWS SDK access (no static AWS keys in pipeline).
-   Never store DB credentials in repository.
-   Prefer Secrets Manager / SSM Parameter Store for production secrets.

---

## 13) Optional Next Improvements

-   Blue/Green deployment (CodeDeploy)
-   Canary releases behind ALB
-   Containerize and deploy on ECS/EKS
-   Add SAST/dependency scans in CI