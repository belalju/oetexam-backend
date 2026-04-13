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
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

This builds the image from source and starts both the app and database containers.

### 4. Verify

```bash
docker compose -f docker-compose.prod.yml ps
docker logs oetexam-app --tail 50
curl http://localhost:8088/actuator/health
```

---

## Updating

Pull the latest code and rebuild:

```bash
git pull
docker compose -f docker-compose.prod.yml --env-file .env up -d --build --no-deps app
```

---

## Useful Commands

```bash
# View live logs
docker logs -f oetexam-app

# Stop containers
docker compose -f docker-compose.prod.yml down

# Stop and wipe all data (irreversible)
docker compose -f docker-compose.prod.yml down -v

# Open a shell inside the app container
docker exec -it oetexam-app sh
```

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
