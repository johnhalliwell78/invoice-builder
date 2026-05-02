.PHONY: help infra-up infra-down infra-logs backend-run backend-test backend-build frontend-install frontend-run frontend-build frontend-lint all-up all-down clean

COMPOSE := docker compose -f docker/docker-compose.yml
COMPOSE_APP := $(COMPOSE) --profile app

help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-22s\033[0m %s\n", $$1, $$2}'

# --- Infrastructure (postgres + redis + mailhog) ---

infra-up: ## Start postgres, redis, mailpit
	$(COMPOSE) up -d postgres redis mailpit

infra-down: ## Stop infra services
	$(COMPOSE) down

infra-logs: ## Tail infra logs
	$(COMPOSE) logs -f postgres redis mailpit

# --- Backend ---

backend-run: ## Run backend in dev mode
	cd backend && ./gradlew bootRun

backend-test: ## Run backend tests
	cd backend && ./gradlew test

backend-build: ## Build backend jar
	cd backend && ./gradlew clean build

# --- Frontend ---

frontend-install: ## Install frontend dependencies
	cd frontend && pnpm install

frontend-run: ## Run frontend dev server
	cd frontend && pnpm dev

frontend-build: ## Production build
	cd frontend && pnpm build

frontend-lint: ## Lint frontend
	cd frontend && pnpm lint

# --- Full stack via Docker Compose ---

all-up: ## Start all services (infra + backend + frontend)
	$(COMPOSE_APP) up -d --build

all-down: ## Stop all services
	$(COMPOSE_APP) down

# --- Clean ---

clean: ## Clean build outputs
	cd backend && ./gradlew clean || true
	rm -rf frontend/dist frontend/node_modules/.vite
