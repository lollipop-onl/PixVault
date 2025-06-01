# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PixVault is a low-cost photo and video storage solution using AWS S3 Glacier as an alternative to Google Photos and iCloud. The project uses a monorepo structure with Scala/Play Framework backend and planned React/TypeScript frontend.

## Key Commands

### Development
```bash
npm run dev              # Start Docker services + run frontend & backend
npm run dev:backend      # Run backend only (cd backend && sbt run)
npm run docker:up        # Start PostgreSQL, LocalStack, and MinIO
npm run docker:down      # Stop all Docker services
npm run db:shell         # Connect to PostgreSQL shell
```

### Backend (Scala/Play)
```bash
cd backend
sbt run                  # Run application on http://localhost:9000
sbt test                 # Run all tests
sbt "testOnly *AuthControllerSpec"  # Run specific test
sbt dist                 # Create production distribution
```

### Testing
```bash
npm run test             # Run all tests (frontend + backend)
npm run test:backend     # Run backend tests only

# Backend test coverage (requires sbt in PATH)
cd backend
sbt clean coverage test coverageReport  # Generate test coverage report
# Coverage report will be in backend/target/scala-3.7.0/scoverage-report/index.html
```

## Architecture

### Backend Structure
The backend follows a clean architecture with repository pattern:
- **Controllers**: HTTP request handlers in `app/controllers/`
- **Services**: Business logic (e.g., `PasswordService` for BCrypt, JWT service for tokens)
- **Repositories**: Data access layer with trait/implementation pattern
  - `UserRepository` trait defines interface
  - `SlickUserRepository` provides PostgreSQL implementation
- **Models**: Domain entities and database table mappings
- **Dependency Injection**: Guice modules for wiring components

### Database
- PostgreSQL with UUID primary keys
- Play Evolutions for schema migrations (auto-applied in dev mode)
- Evolution files in `backend/conf/evolutions/default/`
- Test user: `tanaka.yuki@example.com` / `SecurePass123!`

### Authentication
- JWT-based authentication with HS256 algorithm
- BCrypt password hashing (10 rounds in production, 4 in tests)
- Token expiration: 24 hours
- Refresh token support

### API Endpoints
All API endpoints use `/v1` prefix. See `docs/openapi.yaml` for full specification.

Implemented:
- `/v1/health` - Health check endpoint
- `/v1/auth/login` - User authentication  
- `/v1/auth/refresh` - Refresh access token

Not yet implemented:
- `/v1/media/*` - Media CRUD operations
- `/v1/storage/*` - Storage management
- `/v1/jobs/*` - Background job monitoring

## Configuration

### Environment Variables
Copy `.env.example` to `.env` for local development. Key variables:
- `DB_*` - PostgreSQL connection settings
- `JWT_SECRET` - JWT signing secret
- `AWS_*` - AWS/LocalStack credentials
- `S3_BUCKET` - MinIO/S3 bucket name

### Test Configuration
`backend/conf/test.conf` disables evolutions and mocks external services for testing.

## Current Implementation Status
- ✅ Authentication system (login/refresh endpoints, JWT)
- ✅ User repository with PostgreSQL
- ✅ Database schema and migrations
- ✅ Docker Compose development environment
- ✅ Health check endpoint
- ✅ API versioning (all endpoints use `/v1` prefix)
- ✅ Test coverage setup (scoverage plugin)
- ⏳ Frontend not yet initialized
- ⏳ Media upload/storage features
- ⏳ S3/Glacier integration
- ⏳ Background job processing

## Development Tips
- The backend uses Play Framework's hot reload - changes are applied automatically
- Database migrations run automatically in dev mode
- Use Bruno API client in `api-client/` for testing endpoints
- Check `backend/logs/application.log` for detailed logs

## Git Commit Message Convention
Follow the Conventional Commits specification:
- `feat:` New feature
- `fix:` Bug fix
- `docs:` Documentation changes
- `style:` Code style changes (formatting, missing semicolons, etc.)
- `refactor:` Code refactoring
- `test:` Adding or updating tests
- `chore:` Maintenance tasks, dependency updates

Examples:
- `feat: add user profile image upload`
- `fix: resolve JWT token expiration issue`
- `docs: update API documentation for media endpoints`