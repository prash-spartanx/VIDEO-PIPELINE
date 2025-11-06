-- Bring title in line with entity (1024)
ALTER TABLE press_releases
  ALTER COLUMN title TYPE VARCHAR(1024);

-- Link already 1024 in your entity; enforce it just in case
ALTER TABLE press_releases
  ALTER COLUMN link TYPE VARCHAR(1024);

-- Content must be TEXT (idempotent)
ALTER TABLE press_releases
  ALTER COLUMN content TYPE TEXT;
