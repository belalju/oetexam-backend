# CI/CD Setup (GitHub Actions + GHCR)

The pipeline lives in `.github/workflows/ci-cd.yml`. Everything runs on
**GitHub-hosted runners** — no self-hosted runner or local registry is needed.

| Stage               | Runs on       | Trigger                           | What it does                                                                                                    |
|---------------------|---------------|-----------------------------------|------------------------------------------------------------------------------------------------------------------|
| Build & Test        | GitHub-hosted | every PR and push to `main`       | `mvn verify` on JDK 21 (Temurin) with Maven dependency caching; uploads Surefire reports on failure               |
| Docker Build & Push | GitHub-hosted | push to `main` only               | Builds the image with Buildx (GitHub Actions layer cache) and pushes it to **GHCR**, tagged `latest` and the SHA |
| Deploy              | GitHub-hosted | push to `main`, only when enabled | SSH to the app server, `docker login` to GHCR, pull the new image, `docker compose up -d`                        |

Flow: **build image on GitHub's runner → push to `ghcr.io/<owner>/<repo>` →
deploy server pulls from GHCR and restarts the app**.

The image name is derived from the repository and lowercased in the workflow
(GHCR requires lowercase), e.g. `ghcr.io/<owner>/oetexam`.

## 1. GHCR authentication

- **Pushing (CI):** nothing to configure. The workflow uses the built-in
  `GITHUB_TOKEN` with `packages: write` permission.
- **Pulling (app server):** the deploy step logs the app server in to GHCR with
  the `GHCR_PULL_TOKEN` secret. Create a **classic personal access token**
  (github.com → Settings → Developer settings → Personal access tokens →
  Tokens (classic)) with the **`read:packages`** scope and store it as a
  repository secret (see section 3). The workflow fails fast with a clear
  error if this secret is missing.

The first push creates the GHCR package as **private**. Keep it private; the
pull token grants the app server access. (If the token owner isn't the package
owner, grant them read access under the package's settings.)

## 2. Repository variables (Settings → Secrets and variables → Actions → Variables)

| Variable         | Value                                                                                        |
|------------------|-----------------------------------------------------------------------------------------------|
| `DEPLOY_ENABLED` | `true` to enable the deploy job                                                               |
| `DEPLOY_PATH`    | Directory on the app server containing `docker-compose.yml` and `.env`, e.g. `/opt/oetexam`  |

## 3. Repository secrets (Settings → Secrets and variables → Actions → Secrets)

| Secret            | Value                                                                     |
|-------------------|----------------------------------------------------------------------------|
| `DEPLOY_HOST`     | App server hostname or IP — must be **reachable from the internet**, since the SSH connection originates from GitHub's runners |
| `DEPLOY_USER`     | SSH user (must be able to run `docker compose`)                            |
| `DEPLOY_SSH_KEY`  | Private SSH key for that user (contents of the key file, not a path)       |
| `GHCR_PULL_TOKEN` | Classic PAT with `read:packages` scope — used by the app server to pull from GHCR |

> The deploy job targets the `production` GitHub environment, so these can also
> be defined as environment secrets under Settings → Environments → production.
> Either location works; just don't put `GHCR_PULL_TOKEN` under a *different*
> environment.

## 4. App server prerequisites

- Docker Engine with the Compose plugin installed.
- SSH reachable from the public internet (GitHub-hosted runners have no access
  to your LAN). If the server must stay private, put a tunnel in front of it
  (e.g. Tailscale or Cloudflare Tunnel) or move the deploy job back to a
  self-hosted runner.
- `DEPLOY_PATH` contains this repo's `docker-compose.yml` and a `.env` file with the
  runtime secrets (`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, ...).
- MySQL 8 installed **on the host machine** (not in Docker). The app container reaches
  it via `host.docker.internal`, which `docker-compose.yml` maps to the host gateway.

  One-time MySQL setup (Ubuntu example):

  ```bash
  sudo apt install mysql-server
  sudo mysql
  ```

  ```sql
  CREATE DATABASE oet_practice;
  -- '%' host so connections from the Docker bridge network are accepted;
  -- restrict to '172.%' if you prefer to limit it to Docker's subnets
  CREATE USER 'oetapp'@'%' IDENTIFIED BY '<strong-password>';
  GRANT ALL PRIVILEGES ON oet_practice.* TO 'oetapp'@'%';
  FLUSH PRIVILEGES;
  ```

  MySQL must also listen beyond localhost so the container can connect, and on
  port **3308**: in `/etc/mysql/mysql.conf.d/mysqld.cnf` set

  ```ini
  port = 3308
  bind-address = 0.0.0.0   # or the Docker bridge IP 172.17.0.1
  ```

  then `sudo systemctl restart mysql`. If a firewall is active, allow 3308 from
  Docker subnets only (e.g. `sudo ufw allow from 172.16.0.0/12 to any port 3308`) —
  do not expose it publicly.
- Set `DB_USERNAME`/`DB_PASSWORD` in `.env` to the MySQL user created above. The JDBC
  URL defaults to `jdbc:mysql://host.docker.internal:3308/oet_practice`; override with
  `DB_URL` in `.env` if your setup differs.
- The deploy step sets `APP_IMAGE` to the freshly pushed GHCR tag
  (`ghcr.io/<owner>/<repo>:<commit-sha>`), so the app server never builds the
  image itself.

## 5. (Optional) Protect the `production` environment

The deploy job targets the `production` GitHub environment. Under
Settings → Environments → production you can add required reviewers to make
deploys manual-approval only.

## Rollback

Every push to `main` publishes an immutable SHA tag in GHCR. To roll back,
SSH to the app server and run:

```bash
cd /opt/oetexam
echo "<GHCR_PULL_TOKEN>" | docker login ghcr.io -u <github-username> --password-stdin
export APP_IMAGE=ghcr.io/<owner>/<repo>:<old-commit-sha>
docker compose pull app && docker compose up -d
```

Available tags are listed on GitHub under the repository's **Packages** section.