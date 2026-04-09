-- V2__split_ticket_key.sql

-- 1. Add the new columns (allowing NULL temporarily so we don't break existing rows)
ALTER TABLE tickets ADD COLUMN project_key VARCHAR(10);
ALTER TABLE tickets ADD COLUMN ticket_number BIGINT;

-- 2. Migrate existing data! (PostgreSQL magic to split 'SD3C-34297' into 'SD3C' and 34297)
UPDATE tickets
SET project_key = split_part(ticket_key, '-', 1),
    ticket_number = CAST(split_part(ticket_key, '-', 2) AS BIGINT)
WHERE ticket_key LIKE '%-%';

-- 3. Now that data is safely moved, enforce the NOT NULL constraints
ALTER TABLE tickets ALTER COLUMN project_key SET NOT NULL;
ALTER TABLE tickets ALTER COLUMN ticket_number SET NOT NULL;

-- 4. Create the new composite unique constraint
ALTER TABLE tickets ADD CONSTRAINT unique_project_ticket UNIQUE (project_key, ticket_number);

-- 5. Drop the old column
ALTER TABLE tickets DROP COLUMN ticket_key;