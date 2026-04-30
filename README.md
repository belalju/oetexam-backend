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

## Database Backup & Restore

### Manual backup

Dumps the `oet_practice` database to a timestamped SQL file on the host:

```bash
docker exec oetexam-db \
  mysqldump -u"$DB_USERNAME" -p"$DB_PASSWORD" oet_practice \
  > ./backups/oet_practice_$(date +%Y%m%d_%H%M%S).sql
```
```
sudo docker exec oetexam-db \
mysqldump -u oetuser -pBelalju@123 oet_practice \
> oet_practice_$(date +%Y%m%d_%H%M%S).sql
```

Create the backups directory first if it does not exist:

```bash
mkdir -p ./backups
```

### Restore from a backup

```bash
docker exec -i oetexam-db \
  mysql -u"$DB_USERNAME" -p"$DB_PASSWORD" oet_practice \
  < ./backups/<backup-file>.sql
```

### Automate daily backups (Linux/macOS cron)

Add this line to your crontab (`crontab -e`) to run a backup every day at 02:00:

```
0 2 * * * cd /path/to/oetexam && mkdir -p ./backups && docker exec oetexam-db mysqldump -u"$DB_USERNAME" -p"$DB_PASSWORD" oet_practice > ./backups/oet_practice_$(date +\%Y\%m\%d_\%H\%M\%S).sql
```

> Replace `/path/to/oetexam` with the absolute path to the project directory.  
> `DB_USERNAME` and `DB_PASSWORD` must be set in the shell environment or replaced with their actual values in the cron entry.

### Prune old backups

Keep only the last 30 days of backups:

```bash
find ./backups -name "oet_practice_*.sql" -mtime +30 -delete
```

Add this after the `mysqldump` line in the cron entry to run it automatically.

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
