-- Users
CREATE TABLE IF NOT EXISTS users (
    id           BIGSERIAL PRIMARY KEY,
    username     VARCHAR(100) NOT NULL UNIQUE,
    email        VARCHAR(320),
    password     VARCHAR(255) NOT NULL,
    role         VARCHAR(50)  NOT NULL,  -- e.g., ROLE_ADMIN / ROLE_USER
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Press Releases
CREATE TABLE IF NOT EXISTS press_releases (
    id            BIGSERIAL PRIMARY KEY,
    prid          BIGINT UNIQUE,                    -- PIB PRID
    title         VARCHAR(1024) NOT NULL,           -- was 255: can overflow -> 1024
    link          VARCHAR(1024) NOT NULL,           -- was 255: can overflow -> 1024
    content       TEXT NOT NULL,                    -- large body
    language      VARCHAR(50),                      -- 'english' etc.
    published_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    user_id       BIGINT REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_press_releases_prid ON press_releases(prid);
CREATE INDEX IF NOT EXISTS idx_press_releases_published_at ON press_releases(published_at DESC);

-- Generated Videos
CREATE TABLE IF NOT EXISTS generated_videos (
    id               BIGSERIAL PRIMARY KEY,
    press_release_id BIGINT NOT NULL REFERENCES press_releases(id) ON DELETE CASCADE,
    user_id          BIGINT REFERENCES users(id),
    job_id           VARCHAR(64),
    status           VARCHAR(32) NOT NULL,          -- PENDING / PROCESSING / COMPLETED / FAILED
    language         VARCHAR(32),
    video_url        VARCHAR(1024),
    platform         VARCHAR(64),
    published_url    VARCHAR(1024),
    error_message    TEXT,                          -- long errors (429 etc.)
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_generated_videos_status ON generated_videos(status);
CREATE INDEX IF NOT EXISTS idx_generated_videos_press_release ON generated_videos(press_release_id);
