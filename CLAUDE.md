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

## Media API Implementation Plan

### Phase 1: Data Layer (High Priority)
1. **MediaItem Model** (`backend/app/models/MediaItem.scala`)
   - Core media entity with UUID primary key
   - Support for photos and videos
   - File hash (SHA-256) for duplicate detection
   - Metadata fields (EXIF, location, tags)
   - Storage status tracking (ACTIVE, ARCHIVING, ARCHIVED, RESTORING)

2. **Database Schema** (`backend/conf/evolutions/default/2.sql`)
   - `media_items` table with comprehensive metadata
   - `media_tags` junction table for tag relationships
   - Unique index on `file_hash` for duplicate prevention
   - Indexes on userId, type, uploadedAt for performance

3. **MediaRepository** (`backend/app/repositories/MediaRepository.scala`)
   - CRUD operations following existing UserRepository pattern
   - Advanced filtering (type, date range, tags, storage status)
   - Pagination support for large media libraries
   - Duplicate checking by file hash (`findByHash` method)
   - Slick implementation (`backend/app/repositories/impl/SlickMediaRepository.scala`)

### Phase 2: Service Layer (Medium Priority)
4. **MediaService** (`backend/app/services/MediaService.scala`)
   - File upload processing and validation
   - SHA-256 hash calculation during upload
   - Duplicate file detection before S3 upload
   - Metadata extraction (EXIF for photos, duration for videos)
   - Thumbnail and preview generation
   - Integration with StorageService for S3 operations

5. **StorageService** (`backend/app/services/StorageService.scala`)
   - S3/MinIO file operations
   - Presigned URL generation for secure downloads
   - Glacier archiving and restoration workflows
   - Storage class management (STANDARD → GLACIER → DEEP_ARCHIVE)

6. **MetadataExtractorService** (`backend/app/services/MetadataExtractorService.scala`)
   - EXIF data extraction from images
   - Video metadata extraction (duration, resolution, codec)
   - GPS coordinate processing
   - Camera and lens information parsing

### Phase 3: Controller Layer (Medium Priority)
7. **MediaController** (`backend/app/controllers/MediaController.scala`)
   - `GET /v1/media` - List media with filtering and pagination
   - `POST /v1/media` - Upload new media files (single)
   - `POST /v1/media/batch` - Batch upload (Phase 1: parallel calls, Phase 2: ZIP processing)
   - `GET /v1/media/:id` - Get specific media details
   - `PUT /v1/media/:id` - Update media metadata
   - `DELETE /v1/media/:id` - Delete media
   - `GET /v1/media/:id/download` - Download with quality options
   - `POST /v1/media/:id/archive` - Archive to Glacier
   - `POST /v1/media/:id/restore` - Restore from Glacier

8. **File Upload Handling**
   - Multipart form processing for large files (up to 50GB)
   - File type validation:
     - Images: JPEG, PNG, HEIC, HEIF, TIFF, BMP
     - RAW formats: CR2, NEF, ARW, RAF, ORF, DNG, etc.
     - Videos: MP4, MOV, AVI, MKV (including 4K/8K resolution)
   - SHA-256 hash calculation on upload stream (0.5-4 seconds per GB)
   - Duplicate detection workflow:
     1. Calculate file hash during upload
     2. Check database for existing hash
     3. If duplicate exists, return existing media metadata
     4. If new, proceed with S3 upload and metadata processing
   - Size limits: 50GB maximum per file
   - Temporary file management with streaming processing

### Phase 4: Additional Features (Low Priority)
9. **Background Job System**
   - Job models for async operations (archiving, thumbnail generation, batch processing)
   - JobController for monitoring background tasks (`GET /v1/jobs/:id`)
   - Integration with Play's actor system or external queue
   - Batch upload ZIP processing with progress tracking

10. **Upload Progress & Monitoring**
    - Streaming upload progress for frontend
    - WebSocket or Server-Sent Events for real-time updates
    - Polling endpoint for batch upload progress
    - Upload resumption handling via hash-based duplicate detection

11. **Advanced Search & Metadata**
    - Full-text search across descriptions and tags
    - GPS-based location search (radius queries)
    - Camera equipment filtering (brand, model, lens)
    - Date range queries with timezone support
    - EXIF metadata search (ISO, aperture, shutter speed)

12. **Testing Strategy**
    - Unit tests for all services and repositories
    - Integration tests for file upload/download flows
    - Mock S3 service for testing without external dependencies
    - Performance tests for large file operations (up to 50GB)
    - Load testing for concurrent uploads

### Implementation Dependencies
**Required SBT Dependencies** (add to `backend/build.sbt`):
```scala
// AWS SDK for S3 operations
"software.amazon.awssdk" % "s3" % "2.21.29",
"software.amazon.awssdk" % "glacier" % "2.21.29",

// Image processing and metadata extraction
"org.apache.commons" % "commons-imaging" % "1.0.0-alpha5",
"com.drewnoakes" % "metadata-extractor" % "2.18.0",

// File type detection
"org.apache.tika" % "tika-core" % "2.9.1",

// Thumbnail generation
"net.coobird" % "thumbnailator" % "0.4.19",

// File hashing for duplicate detection
"commons-codec" % "commons-codec" % "1.16.0",

// RAW image processing
"org.apache.commons" % "commons-imaging" % "1.0.0-alpha5",

// Video metadata extraction
"org.bytedeco" % "ffmpeg-platform" % "6.0-1.5.9"
```

### File Storage Structure
```
s3://pixvault-media/
├── users/{userId}/
│   └── media/{mediaId}/
│       ├── original.{ext}     # Original file (up to 50GB)
│       ├── preview.{ext}      # Web-optimized version
│       └── thumbnail.webp     # WebP thumbnail (200x200)
└── batch/{batchId}/           # Temporary ZIP files for batch processing
    └── upload.zip
```

### S3 Lifecycle Configuration
```yaml
# Automatic storage class transitions
Rules:
  - Id: "MediaLifecycle"
    Status: Enabled
    Filter:
      Prefix: "users/"
    Transitions:
      - Days: 30          # Standard to Standard-IA
        StorageClass: STANDARD_IA
      - Days: 90          # Standard-IA to Glacier
        StorageClass: GLACIER  
      - Days: 365         # Glacier to Deep Archive
        StorageClass: DEEP_ARCHIVE
    
  - Id: "BatchCleanup"
    Status: Enabled
    Filter:
      Prefix: "batch/"
    Expiration:
      Days: 7             # Auto-delete batch files after 7 days
```

### Implementation Order
1. Models and database schema with hash field (Phase 1: items 1-2)
2. Repository layer with duplicate checking (Phase 1: item 3)
3. Core services with hash calculation (Phase 2: items 4-5)
4. Basic CRUD endpoints with duplicate detection (Phase 3: item 7)
   - **Efficient CRUD Implementation Order:**
     1. `POST /v1/media` - CREATE (most complex, includes file handling)
     2. `GET /v1/media/:id` - READ single (verify created data)
     3. `GET /v1/media` - READ list (pagination, filtering)
     4. `PUT /v1/media/:id` - UPDATE (metadata only, simpler)
     5. `DELETE /v1/media/:id` - DELETE (last to minimize risk)
5. File processing services (Phase 2: item 6)
6. Advanced endpoints (Phase 3: item 7 - download, archive, restore)
7. Background jobs and monitoring (Phase 4)

### Duplicate Detection Benefits
- **Storage Cost Reduction**: Eliminates redundant S3 storage costs
- **Upload Performance**: Instant response for duplicate files (no S3 upload needed)
- **Bandwidth Savings**: Reduces network traffic for duplicate uploads
- **User Experience**: Fast upload completion for already-stored files
- **Data Deduplication**: Efficient storage utilization across all users

### Future AI Features Preparation
**Database Schema Extensions** (add when needed):
```sql
-- AI analysis results
ALTER TABLE media_items ADD COLUMN ai_tags JSONB;
ALTER TABLE media_items ADD COLUMN face_embeddings JSONB;
ALTER TABLE media_items ADD COLUMN object_detection JSONB;
ALTER TABLE media_items ADD COLUMN analyzed_at TIMESTAMP;

-- AI processing queue
CREATE TABLE ai_analysis_jobs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  media_id UUID REFERENCES media_items(id),
  job_type VARCHAR(50) NOT NULL,
  status VARCHAR(20) DEFAULT 'pending',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP
);
```

**Service Architecture Preparation**:
- `AIAnalysisService` interface for future ML integration
- Async processing queue for computationally expensive operations
- Webhook endpoints for external AI service callbacks

### Performance Configuration for Large Files
**Application Settings** (`backend/conf/application.conf`):
```scala
# Large file upload settings
play.http.parser.maxMemoryBuffer = 128MB
play.http.parser.maxDiskBuffer = 50GB
play.server.http.idleTimeout = 1800s      # 30 minutes
play.server.akka.requestTimeout = 1800s

# Streaming settings
akka.http.server.parsing.max-content-length = 50GB
akka.http.server.request-timeout = 1800s

# Database connection pool
play.db.default.hikaricp.maximumPoolSize = 20
play.db.default.hikaricp.minimumIdle = 5

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