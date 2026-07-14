# CI/CD Setup (GitHub Actions + self-hosted runner + local registry)

The pipeline lives in `.github/workflows/ci-cd.yml` and runs these stages:

| Stage               | Runs on                               | Trigger                           | What it does                                                                                                            |
|---------------------|---------------------------------------|-----------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| Build & Test        | GitHub-hosted                         | every PR and push to `main`       | `mvn verify` on JDK 21 (Temurin) with Maven dependency caching; uploads Surefire reports on failure                     |
| Security Scan       | GitHub-hosted                         | every PR and push to `main`       | Trivy filesystem scan of dependencies and config; fails on fixable CRITICAL/HIGH vulnerabilities                        |
| Docker Build & Push | **self-hosted runner (build server)** | push to `main` only               | Builds the image on the build server and pushes it to the **local Docker registry**, tagged `latest` and the commit SHA |
| Deploy              | **self-hosted runner**                | push to `main`, only when enabled | SSH to the app server, pull the new image from the local registry, `docker compose up -d`                               |

Flow: **build image on the local build server → push to local registry → deploy server pulls from the registry and restarts the app**. Nothing is pushed to or pulled from GHCR.

## 1. Local Docker registry (one-time, on the build server or any always-on host)

```bash
docker run -d \
  --name registry \
  --restart=always \
  -p 5000:5000 \
  -v /opt/registry/data:/var/lib/registry \
  registry:2
```

The registry address (e.g. `192.168.1.10:5000`) must be reachable from **both** the build server and the deploy server.

### Plain-HTTP registry: mark it as insecure

Docker refuses HTTP registries by default. On **every machine that pushes or pulls**
(build server *and* deploy server), add the registry to `/etc/docker/daemon.json`:

```json
{
  "insecure-registries": ["192.168.1.10:5000"]
}
```

then `sudo systemctl restart docker`. Skip this if you put TLS in front of the
registry (recommended if it's reachable beyond a trusted network).

### (Optional) Basic auth

If the registry is reachable by anyone other than the two servers, protect it:

```bash
mkdir -p /opt/registry/auth
docker run --rm --entrypoint htpasswd httpd:2 -Bbn oetci '<strong-password>' > /opt/registry/auth/htpasswd

docker run -d \
  --name registry \
  --restart=always \
  -p 5000:5000 \
  -v /opt/registry/data:/var/lib/registry \
  -v /opt/registry/auth:/auth \
  -e REGISTRY_AUTH=htpasswd \
  -e REGISTRY_AUTH_HTPASSWD_REALM="Registry" \
  -e REGISTRY_AUTH_HTPASSWD_PATH=/auth/htpasswd \
  registry:2
```

Then set the repository variable `REGISTRY_AUTH_ENABLED=true` and the
`REGISTRY_USERNAME` / `REGISTRY_PASSWORD` secrets (see below) — the workflow
logs in before pushing/pulling.

## 2. Self-hosted GitHub Actions runner (one-time, on the build server)

Repo → Settings → Actions → Runners → **New self-hosted runner**, then follow the
generated commands on the build server, roughly:

```bash
mkdir ~/actions-runner && cd ~/actions-runner
curl -o actions-runner-linux-x64.tar.gz -L https://github.com/actions/runner/releases/latest/download/actions-runner-linux-x64-<version>.tar.gz
tar xzf actions-runner-linux-x64.tar.gz
./config.sh --url https://github.com/<owner>/<repo> --token <registration-token>
sudo ./svc.sh install && sudo ./svc.sh start
```

Requirements on the runner machine:

- Docker Engine installed, and the runner user in the `docker` group
  (`sudo usermod -aG docker <runner-user>`).
- Network access to the local registry and SSH access to the deploy server
  (the deploy job runs on this runner and SSHes to the app server).

## 3. Repository variables (Settings → Secrets and variables → Actions → Variables)

| Variable                | Value                                                                                       |
|-------------------------|---------------------------------------------------------------------------------------------|
| `REGISTRY_URL`          | Local registry address, e.g. `192.168.1.10:5000`                                            |
| `REGISTRY_AUTH_ENABLED` | `true` only if the registry uses basic auth; otherwise unset                                |
| `DEPLOY_ENABLED`        | `true` to enable the deploy job                                                             |
| `DEPLOY_PATH`           | Directory on the app server containing `docker-compose.yml` and `.env`, e.g. `/opt/oetexam` |

## 4. Repository secrets (Settings → Secrets and variables → Actions → Secrets)

| Secret              | Value                                                                |
|---------------------|----------------------------------------------------------------------|
| `DEPLOY_HOST`       | App server hostname or IP (reachable from the build server)          |
| `DEPLOY_USER`       | SSH user (must be able to run `docker compose`)                      |
| `DEPLOY_SSH_KEY`    | Private SSH key for that user (contents of the key file, not a path) |
| `REGISTRY_USERNAME` | Registry basic-auth user — only if `REGISTRY_AUTH_ENABLED=true`      |
| `REGISTRY_PASSWORD` | Registry basic-auth password — only if `REGISTRY_AUTH_ENABLED=true`  |

## 5. App server prerequisites

- Docker Engine with the Compose plugin installed.
- The registry listed under `insecure-registries` in `/etc/docker/daemon.json`
  (see section 1) unless it serves TLS.
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
- The deploy step sets `APP_IMAGE` to the freshly pushed local-registry tag
  (`<REGISTRY_URL>/oetexam:<commit-sha>`), so the app server never builds the
  image itself.

## 6. (Optional) Protect the `production` environment

The deploy job targets the `production` GitHub environment. Under
Settings → Environments → production you can add required reviewers to make
deploys manual-approval only.

## Rollback

Every push to `main` publishes an immutable SHA tag in the local registry. To
roll back, SSH to the app server and run:

```bash
cd /opt/oetexam
export APP_IMAGE=<REGISTRY_URL>/oetexam:<old-commit-sha>
docker compose pull app && docker compose up -d
```

Tags kept in the registry can be listed with:

```bash
curl http://<REGISTRY_URL>/v2/oetexam/tags/list
```
