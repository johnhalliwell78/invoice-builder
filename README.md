# Invoice Builder

A multi-tenant SaaS invoicing platform for small and medium businesses.

## Tech Stack

- **Frontend:** React 19 + TypeScript + Vite + Tailwind 4 + shadcn/ui + Zustand + TanStack Query
- **Backend:** Java 21 + Spring Boot 3.4 + PostgreSQL 16 + Redis 7
- **Auth:** JWT (access + refresh) + OAuth2 (Google, GitHub)
- **PDF:** iText 7 Community
- **Email:** SendGrid (prod) / Mailhog (dev)
- **Real-time:** WebSocket (Spring STOMP + SockJS)
- **Infra:** Docker Compose / GitHub Actions / GHCR

## Prerequisites

- Java 21 (use [SDKMAN](https://sdkman.io/))
- Node 22+ and pnpm 10+ (use [nvm](https://github.com/nvm-sh/nvm))
- Docker 24+ with Docker Compose
- Make (preinstalled on macOS/Linux)

## Quick Start

```bash
# 1. Bring up infra (postgres, redis, mailpit)
make infra-up

# 2. Run backend (port 8080)
make backend-run

# 3. Run frontend (port 5173)
make frontend-run
```

Then open http://localhost:5173.

## Development

| Command | Description |
|---|---|
| `make infra-up` | Start postgres + redis + mailpit |
| `make infra-down` | Stop infra services |
| `make infra-logs` | Tail infra logs |
| `make backend-run` | Run backend in dev mode |
| `make backend-test` | Run backend unit tests |
| `make frontend-install` | Install frontend deps |
| `make frontend-run` | Run frontend dev server |
| `make frontend-build` | Production build |
| `make all-up` | Full stack via docker compose |
| `make all-down` | Tear down full stack |
| `make clean` | Clean build outputs |

## Useful URLs (dev)

| Service | URL |
|---|---|
| Frontend | http://localhost:5173 |
| Backend API | http://localhost:8080/api/v1 |
| API docs (Swagger) | http://localhost:8080/swagger-ui.html |
| Mailpit UI | http://localhost:8026 |
| Postgres | localhost:5433 (user: `invoice`, db: `invoice_builder`) |
| Redis | localhost:6379 |

## Project Structure

```
invoice-builder/
├── backend/        Spring Boot service
├── frontend/       Vite + React SPA
├── docker/         Compose files + Dockerfiles
├── docs/           Architecture and API spec
└── .github/        CI/CD workflows
```

## License

Proprietary.
