ALTER TABLE refresh_sessions ADD COLUMN device_name VARCHAR(120);
ALTER TABLE refresh_sessions ADD COLUMN platform VARCHAR(40);
ALTER TABLE refresh_sessions ADD COLUMN app_version VARCHAR(40);
