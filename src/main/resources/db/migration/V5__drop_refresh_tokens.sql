-- Drop refresh_tokens table to support pure stateless JWT authentication
DROP TABLE IF EXISTS refresh_tokens CASCADE;
