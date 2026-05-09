-- Create separate schema for Keycloak (business data stays in pfedb)
CREATE DATABASE IF NOT EXISTS keycloak CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON keycloak.* TO 'pfeuser'@'%';
FLUSH PRIVILEGES;
