# Lumen Docker Stack

Full 21-service stack: 3 infra (MySQL 8, Redis 7, Nacos 2.3) + 18 lumen Spring
Boot services + 1 nginx-fronted portal.

## Quick start

```bash
# 1. Build the 18 lumen fat jars (run from lumen-parent/)
cd lumen-parent
mvn package -Dmaven.test.skip=true -pl lumen-gateway,lumen-auth,lumen-platform,lumen-system,lumen-org,lumen-workflow,lumen-file,lumen-message,lumen-hr,lumen-finance,lumen-assets,lumen-contract,lumen-procurement,lumen-sales,lumen-payroll,lumen-inventory,lumen-bi,lumen-mobile

# 2. Build 18 Docker images
cd ..
bash docker/build-images.sh

# 3. Bring the stack up
docker compose -f docker/docker-compose.yml up -d

# 4. Watch healthchecks settle (Nacos takes ~60s, services another ~30s)
docker compose -f docker/docker-compose.yml ps
```

## Layout

```
docker/
├── docker-compose.yml       # 21 services, single lumen-net bridge
├── service.Dockerfile       # Multi-service Java 17 template
├── nginx/
│   └── nginx.conf           # Portal reverse-proxy (SPA + /api -> gateway)
├── init-sql/                # MySQL init: 00-init-nacos-db.sql + 10 lumen Flyway V*
├── build-images.sh          # Build 18 lumen images from fat jars
└── .env                     # Tag + port defaults
```

## Endpoints after `up -d`

| What                | Host URL                          |
|---------------------|-----------------------------------|
| Gateway (entry)     | http://localhost:9200             |
| Nacos console       | http://localhost:8848/nacos (lumen/lumen) |
| MySQL               | localhost:3306 (root/root)        |
| Redis               | localhost:6379 (redis123)         |
| Portal (P6)         | http://localhost:8080 (with `--profile portal`) |

## Image strategy

Single `service.Dockerfile` with `ARG SERVICE` + `ARG JAR_FILE`. Each lumen
service still ends up as a discrete `lumen/lumen-X:dev` image so compose can
scale them independently, but the JRE base layer is shared in BuildKit cache
across all 18 builds.

`lumen-gateway` is the special case — its Spring Boot finalName is overridden
to `lumen-gateway-app.jar` while the others use `lumen-X.jar`. The mapping
table in `build-images.sh` handles both.

## Operational cheatsheet

```bash
# Tail one service
docker compose -f docker/docker-compose.yml logs -f lumen-hr

# Restart a single service after a fat-jar rebuild
docker compose -f docker/docker-compose.yml up -d --force-recreate --no-deps lumen-hr

# Wipe DB + restart from scratch
docker compose -f docker/docker-compose.yml down -v
docker compose -f docker/docker-compose.yml up -d

# E2E smoke from host
TOKEN=$(curl -s -X POST http://localhost:9200/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"userName":"admin","password":"admin123","tenantId":1}' \
  | grep -oE '"accessToken":"[^"]+"' | cut -d'"' -f4)
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer $TOKEN" http://localhost:9200/hr/employee/list
```
