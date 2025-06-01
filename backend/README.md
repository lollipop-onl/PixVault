# PixVault Backend

Play Framework backend for PixVault - Photo and video management application.

## Prerequisites

- Java 17 or higher
- sbt 1.9.0 or higher
- Docker and Docker Compose (for PostgreSQL)
- Bruno (API client) for testing

## Setup

### 1. Start PostgreSQL

```bash
# From project root directory
docker-compose up -d postgres
```

### 2. Run the Play application

```bash
cd backend
sbt run
```

The application will:
- Start on http://localhost:9000
- Automatically apply database migrations (Play Evolutions)
- Create the initial test user

## Testing the Login Endpoint

### Using Bruno

1. Open Bruno and load the collection from `api-client/pixvault`
2. Select "Local" environment
3. Run "Health Check" to verify the server is running
4. Run "Login" to test authentication

### Using curl

```bash
# Health check
curl http://localhost:9000/health

# Login
curl -X POST http://localhost:9000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "tanaka.yuki@example.com",
    "password": "SecurePass123!"
  }'
```

### Test Credentials

- Email: `tanaka.yuki@example.com`
- Password: `SecurePass123!`

## Database

The application uses PostgreSQL with the following configuration:
- Host: localhost
- Port: 5432
- Database: pixvault
- User: pixvault
- Password: pixvault

Database schema is managed by Play Evolutions in `conf/evolutions/default/`.

## Architecture

- **Controllers**: Handle HTTP requests and responses
- **Services**: Business logic (e.g., PasswordService)
- **Repositories**: Data access layer using Slick
- **Models**: Domain models and database tables

## Configuration

Main configuration file: `conf/application.conf`

Key configurations:
- Database connection
- S3/MinIO settings
- Application secrets
- Upload limits