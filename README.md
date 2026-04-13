# OET Practice Test — Deployment Guide

## Prerequisites

- Docker and Docker Compose installed on both local machine and server
- SSH access to the target server
- A deployment directory on the server (e.g. `/opt/oetexam`)

---

## Local Machine

### 1. Build the Docker image

```bash
docker build -t oetexam:latest .
```

### 2. Export the image to a file

```bash
docker save oetexam:latest | gzip > oetexam.tar.gz
```

### 3. Transfer files to the server

```bash
scp oetexam.tar.gz          user@your-server:/opt/oetexam/
scp docker-compose.prod.yml user@your-server:/opt/oetexam/
scp .env.prod.example       user@your-server:/opt/oetexam/
```

---

## Server (first-time setup)

### 4. SSH into the server

```bash
ssh user@your-server
cd /opt/oetexam
```

### 5. Create the environment file

```bash
cp .env.prod.example .env
nano .env
```

Fill in all values. To generate a secure JWT secret:

```bash
openssl rand -base64 64
```

### 6. Load the Docker image

```bash
docker load < oetexam.tar.gz
```

### 7. Start the application

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d
```

### 8. Verify containers are running

```bash
docker compose -f docker-compose.prod.yml ps
docker logs oetexam-app --tail 50
```

The application will be available at `http://your-server:8080`.

---

## Updating the Application

Run these steps each time you deploy a new version.

### On local machine

```bash
docker build -t oetexam:latest .
docker save oetexam:latest | gzip > oetexam.tar.gz
scp oetexam.tar.gz user@your-server:/opt/oetexam/
```

### On the server

```bash
cd /opt/oetexam
docker load < oetexam.tar.gz
docker compose -f docker-compose.prod.yml --env-file .env up -d --no-deps app
```

The `--no-deps app` flag restarts only the app container, leaving the database untouched.

---

## Useful Commands

```bash
# View live logs
docker logs -f oetexam-app

# Stop everything
docker compose -f docker-compose.prod.yml down

# Stop and delete all data (irreversible)
docker compose -f docker-compose.prod.yml down -v

# Open a shell inside the running app container
docker exec -it oetexam-app sh

# Check health endpoint
curl http://localhost:8080/actuator/health
```

---

## Environment Variables Reference

| Variable | Required | Description |
|---|---|---|
| `DB_USERNAME` | Yes | MySQL username |
| `DB_PASSWORD` | Yes | MySQL password |
| `JWT_SECRET` | Yes | Secret key for signing JWTs (min 64 chars) |
| `JWT_ACCESS_EXPIRATION_MS` | No | Access token TTL in ms (default: 3600000 = 1h) |
| `JWT_REFRESH_EXPIRATION_MS` | No | Refresh token TTL in ms (default: 604800000 = 7d) |

---

## Notes

- The `.env` file must never be committed to version control.
- The MySQL port is bound to `127.0.0.1` only — it is not publicly accessible.
- Audio uploads are stored in a named Docker volume (`audio_uploads`) and persist across container restarts.
- Swagger UI is disabled in the `prod` profile.
