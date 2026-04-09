-- Clean up existing data so Spring Boot doesn't crash when reading old tickets

-- Map all 'open' type statuses to the new Enum constant
UPDATE tickets
SET status = 'SECOND_LEVEL'
WHERE status IN ('Offen', 'In Bearbeitung', 'Open', '2ND Level');

-- Map all 'done' type statuses to the new Enum constant
UPDATE tickets
SET status = 'ERLEDIGT'
WHERE status IN ('Erledigt', 'Done', 'Closed');

-- Map any paused statuses to the new Enum constant
UPDATE tickets
SET status = 'SLA_AUSGESETZT'
WHERE status IN ('Pausiert', 'SLA ausgesetzt', 'Waiting');

-- (Optional but recommended) Ensure all null statuses have a default
UPDATE tickets
SET status = 'SECOND_LEVEL'
WHERE status IS NULL;