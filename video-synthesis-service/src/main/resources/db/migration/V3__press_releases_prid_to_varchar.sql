-- Convert PRID from BIGINT to VARCHAR(64) safely (idempotent if already text)
ALTER TABLE press_releases
    ALTER COLUMN prid TYPE VARCHAR(64) USING prid::text;

-- Ensure NOT NULL
ALTER TABLE press_releases
    ALTER COLUMN prid SET NOT NULL;

-- Ensure a unique constraint exists on PRID (ignore if already present)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_press_releases_prid'
          AND conrelid = 'press_releases'::regclass
    ) THEN
        ALTER TABLE press_releases
            ADD CONSTRAINT uk_press_releases_prid UNIQUE (prid);
    END IF;
END$$;
