# CI/CD Setup Manual (Windows/macOS -> GitHub Actions -> EC2)

This guide combines local setup steps for both Windows and macOS.

## Related Manuals

- Project/API setup: `README.md`
- EC2 setup (Windows): `EC2_SETUP_MANUAL_WINDOWS.md`
- EC2 setup (macOS): `EC2_SETUP_MANUAL_MAC.md`

## 1) Deployment Flow

`push/merge to main` -> GitHub Actions -> build/test -> upload JAR -> restart `todo` service.

## 2) Prerequisites

- EC2 deployment already working once
- `todo` systemd service configured on EC2
- GitHub repository admin/maintainer access (or equivalent branch/workflow permissions)
- SSH access to EC2

## 3) Prepare EC2 for CI Deploy

On EC2:

```bash
mkdir -p /home/ec2-user/app/releases /home/ec2-user/app/current
sudo tee /etc/sudoers.d/todo-deploy > /dev/null <<'EOF'
ec2-user ALL=(ALL) NOPASSWD: /bin/systemctl restart todo, /bin/systemctl is-active todo
EOF
sudo chmod 440 /etc/sudoers.d/todo-deploy
```

Ensure `todo.service` uses:

```ini
ExecStartPre=/home/ec2-user/app/bin/load-env-from-ssm.sh
ExecStart=/usr/bin/java -jar /home/ec2-user/app/current/app.jar
```

Notes:
- DB credentials should be read from AWS Systems Manager Parameter Store, not GitHub secrets.
- The loader script path should be `/home/ec2-user/app/bin/load-env-from-ssm.sh`.
- Full setup steps for Parameter Store + loader script are in `EC2_SETUP_MANUAL_WINDOWS.md` and `EC2_SETUP_MANUAL_MAC.md`.

## 4) Generate Deploy Key

### Option A: macOS (Terminal / zsh)

```bash
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/github_actions_deploy_key
```

Add public key to EC2:

```bash
scp -i ~/.ssh/ms-mfe-demo-key.pem ~/.ssh/github_actions_deploy_key.pub ec2-user@<ec2-public-ip>:/home/ec2-user/
ssh -i ~/.ssh/ms-mfe-demo-key.pem ec2-user@<ec2-public-ip> "cat ~/github_actions_deploy_key.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
```

### Option B: Windows (PowerShell)

```powershell
ssh-keygen -t ed25519 -C "github-actions-deploy" -f "$env:USERPROFILE\.ssh\github_actions_deploy_key"
```

Verify the key files exist before continuing:

```powershell
Test-Path "$env:USERPROFILE\.ssh\github_actions_deploy_key"
Test-Path "$env:USERPROFILE\.ssh\github_actions_deploy_key.pub"
```

Add public key to EC2:

```powershell
scp -i "C:\Users\<you>\.ssh\ms-mfe-demo-key.pem" "$env:USERPROFILE\.ssh\github_actions_deploy_key.pub" ec2-user@<ec2-public-ip>:/home/ec2-user/
ssh -i "C:\Users\<you>\.ssh\ms-mfe-demo-key.pem" ec2-user@<ec2-public-ip> "cat ~/github_actions_deploy_key.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
```

If `scp` shows `stat local ... No such file or directory`, regenerate the deploy key and retry the same commands.

## 5) Configure GitHub Secrets

In `Settings -> Secrets and variables -> Actions`, add:

- `EC2_HOST`
- `EC2_USER` (`ec2-user`)
- `EC2_SSH_KEY` (private key content of `github_actions_deploy_key`)
- `EC2_APP_DIR` (`/home/ec2-user/app`)
- optional `APP_HEALTHCHECK_PATH` (`/api/todos`)

Do not add DB username/password to GitHub secrets when using Parameter Store.

## 6) Workflow File

Create/update `.github/workflows/deploy-ec2.yml` (already included in this repo).

Current workflow includes:

- required secret validation
- build/test/package
- upload release JAR
- symlink switch + service restart
- optional localhost health check over SSH

## 7) Test Pipeline

1. Create test branch and push a harmless change.
2. Open PR to `main`, merge (or trigger `workflow_dispatch`).
3. In Actions run, verify green steps:
  - build/test
  - setup SSH
  - upload JAR
  - restart service
  - health check (optional)

## 8) Troubleshooting

- Missing secret error: verify exact secret names.
- SSH fail: confirm `authorized_keys` contains deploy public key.
- Service restart fail: verify sudoers + service file path.
- Startup fail after restart: check `journalctl -u todo -n 100 --no-pager` for `AccessDeniedException`, `ParameterNotFound`, or KMS decrypt errors.
- Health check fail: verify app binds to `8080` and `APP_HEALTHCHECK_PATH`.
