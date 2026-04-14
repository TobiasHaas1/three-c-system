-- Remove the old columns
ALTER TABLE tempo_bookings DROP COLUMN booking_date;
ALTER TABLE tempo_bookings DROP COLUMN duration;

-- Add the new columns for precise timing
ALTER TABLE tempo_bookings ADD COLUMN start_time TIMESTAMP;
ALTER TABLE tempo_bookings ADD COLUMN end_time TIMESTAMP;