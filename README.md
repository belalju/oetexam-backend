# OET Practice Test

Spring Boot 3.x backend for OET (Occupational English Test) practice tests.

---

## Prerequisites

- Docker and Docker Compose
- Git

---

## Setup

### 1. Clone the repository

```bash
git clone <repo-url>
cd oetexam
```

### 2. Create the environment file

```bash
cp .env.prod.example .env
nano .env
```

Generate a secure JWT secret:

```bash
openssl rand -base64 64
```

### 3. Build and start

```bash
docker compose --env-file .env up -d --build
```

This builds the image from source and starts both the app and database containers.

### 4. Verify

```bash
docker compose ps
docker logs oetexam-app --tail 50
curl http://localhost:8088/actuator/health
```

---

## Updating

Pull the latest code and rebuild:

```bash
git pull
docker compose  --env-file .env up -d --build --no-deps app
```

---

## Useful Commands

```bash
# View live logs
docker logs -f oetexam-app

# Stop containers
docker compose  down

# Stop and wipe all data (irreversible)
docker compose  down -v

# Open a shell inside the app container
docker exec -it oetexam-app sh
```

---

## Logs

Only `ERROR` level entries are written to file. Logs are stored in a named Docker volume (`app_logs`) mounted at `/app/logs` inside the container.

### Tail errors live

```bash
docker exec oetexam-app tail -f /app/logs/error.log
```

### Copy log file to the current host directory

```bash
docker cp oetexam-app:/app/logs/error.log ./error.log
```

### Access via Docker volume path (Linux hosts only)

```bash
# Find the volume mount path on the host
docker volume inspect oetexam_app_logs

# Logs are at
/var/lib/docker/volumes/oetexam_app_logs/_data/error.log
```

Logs rotate daily and are kept for 30 days (max 500 MB total). Rotated files are named `error.YYYY-MM-DD.log`.

---

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `DB_USERNAME` | Yes | MySQL username |
| `DB_PASSWORD` | Yes | MySQL password |
| `JWT_SECRET` | Yes | JWT signing key (min 64 chars) |
| `JWT_ACCESS_EXPIRATION_MS` | No | Access token TTL in ms (default: 3600000 = 1h) |
| `JWT_REFRESH_EXPIRATION_MS` | No | Refresh token TTL in ms (default: 604800000 = 7d) |

> The `.env` file must never be committed to version control.
