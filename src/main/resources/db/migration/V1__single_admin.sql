-- Create unique partial index to ensure only one admin can exist at a time
CREATE UNIQUE INDEX IF NOT EXISTS uq_single_admin ON users (role) WHERE role = 'ROLE_ADMIN';
