-- Drop duplicate profile URL columns from users table if they exist
-- (as this data is now managed in the profiles table)

ALTER TABLE users DROP COLUMN IF EXISTS profile_url;
ALTER TABLE users DROP COLUMN IF EXISTS profile_picture_url;
