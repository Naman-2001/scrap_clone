# Dcluttr Scraper Service

A robust and scalable web scraping application built with Spring Boot and Temporal Workflow Engine.

## Features

- Highly scalable scraping architecture using Temporal workflows
- Distributed rate limiting with Redis
- ClickHouse integration for high-performance data storage
- Proxy support for avoiding IP blocks
- Support for multiple scraping services (Blinkit, Zepto)

## Environment Variables

The application uses the following environment variables:

### ClickHouse Connection
- `CLICKHOUSE_HOST` - ClickHouse server hostname
- `CLICKHOUSE_PORT` - ClickHouse server port (default: 8123)
- `CLICKHOUSE_DB` - ClickHouse database name
- `CLICKHOUSE_USER` - ClickHouse username
- `CLICKHOUSE_PASSWORD` - ClickHouse password

### Temporal Workflow Engine
- `TEMPORAL_HOST` - Temporal server hostname
- `TEMPORAL_PORT` - Temporal server port (default: 7233)
- `TASK_QUEUE` - Default Temporal task queue
- `TASK_QUEUE_BLINKIT` - Task queue for Blinkit scraping service
- `TASK_QUEUE_ZEPTO` - Task queue for Zepto scraping service

### Redis Configuration
- `REDIS_HOST` - Redis server hostname
- `REDIS_PORT` - Redis server port (default: 6379)

### Proxy Settings
- `PROXY_USERNAME` - Proxy username
- `PROXY_PASSWORD` - Proxy password
- `PROXY_DNS` - Proxy DNS in the format `host:port`

## Running Locally

### Prerequisites
- Java 24 or later
- Maven 3.6 or later
- Temporal server
- ClickHouse database
- Redis server

### Build
```bash
mvn clean package
```

### Run
```bash
java -jar target/dcluttr-scrapper-0.0.1-SNAPSHOT.jar
```

## Running with Docker

```bash
# Build the Docker image
docker build -t dcluttr-scrapper:latest .

# Run the Docker container
docker run -p 9000:9000 \
  -e CLICKHOUSE_HOST=clickhouse-host \
  -e CLICKHOUSE_PORT=443 \
  -e CLICKHOUSE_DB=dcluttr \
  -e CLICKHOUSE_USER=admin \
  -e CLICKHOUSE_PASSWORD=password \
  -e TEMPORAL_HOST=temporal-host \
  -e TEMPORAL_PORT=7233 \
  -e REDIS_HOST=redis-host \
  -e REDIS_PORT=6379 \
  -e PROXY_USERNAME=username \
  -e PROXY_PASSWORD=password \
  -e PROXY_DNS=host:port \
  -e TASK_QUEUE=scraper-task-queue \
  -e TASK_QUEUE_BLINKIT=blinkit-scraping-task-queue \
  -e TASK_QUEUE_ZEPTO=zepto-scraping-task-queue \
  dcluttr-scrapper:latest
```

## API Endpoints

- `POST /api/scraper/start` - Start the default (Blinkit) scraping workflow
- `POST /api/scraper/start/{service}` - Start a specific scraping workflow (blinkit or zepto)
- `GET /api/scraper/status/{workflowId}` - Check the status of a workflow
- `DELETE /api/scraper/cancel/{workflowId}` - Cancel a running workflow

## Monitoring

Health and metrics endpoints are available at:
- `/actuator/health` - Health status
- `/actuator/metrics` - Application metrics
- `/actuator/prometheus` - Prometheus metrics 