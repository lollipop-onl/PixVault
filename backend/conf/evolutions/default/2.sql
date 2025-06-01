# --- !Ups

CREATE TABLE media_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(10) NOT NULL CHECK (type IN ('photo', 'video')),
    filename VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_hash VARCHAR(64) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size BIGINT NOT NULL,
    width INTEGER,
    height INTEGER,
    duration DOUBLE PRECISION,
    description TEXT,
    storage_class VARCHAR(20) NOT NULL DEFAULT 'STANDARD' CHECK (storage_class IN ('STANDARD', 'STANDARD_IA', 'GLACIER', 'DEEP_ARCHIVE')),
    storage_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (storage_status IN ('ACTIVE', 'ARCHIVING', 'ARCHIVED', 'RESTORING')),
    thumbnail_url VARCHAR(500),
    preview_url VARCHAR(500),
    original_url VARCHAR(500),
    captured_at TIMESTAMP,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMP,
    last_accessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_file_hash UNIQUE (file_hash)
);

-- JSON columns for location and metadata
ALTER TABLE media_items ADD COLUMN location JSONB;
ALTER TABLE media_items ADD COLUMN metadata JSONB;

-- Indexes for performance
CREATE INDEX idx_media_items_user_id ON media_items(user_id);
CREATE INDEX idx_media_items_type ON media_items(type);
CREATE INDEX idx_media_items_uploaded_at ON media_items(uploaded_at DESC);
CREATE INDEX idx_media_items_captured_at ON media_items(captured_at DESC);
CREATE INDEX idx_media_items_file_hash ON media_items(file_hash);
CREATE INDEX idx_media_items_storage_status ON media_items(storage_status);

-- Media tags table for many-to-many relationship
CREATE TABLE media_tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    media_id UUID NOT NULL REFERENCES media_items(id) ON DELETE CASCADE,
    tag VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_media_tag UNIQUE (media_id, tag)
);

-- Index for tag queries
CREATE INDEX idx_media_tags_tag ON media_tags(tag);
CREATE INDEX idx_media_tags_media_id ON media_tags(media_id);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger to automatically update updated_at
CREATE TRIGGER update_media_items_updated_at BEFORE UPDATE
    ON media_items FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

# --- !Downs

DROP TRIGGER IF EXISTS update_media_items_updated_at ON media_items;
DROP FUNCTION IF EXISTS update_updated_at_column();
DROP TABLE IF EXISTS media_tags;
DROP TABLE IF EXISTS media_items;