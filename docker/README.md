# Docker Infra

Cette stack Docker sert uniquement à l'infrastructure d'identité:

- MySQL pour Keycloak
- Keycloak
- phpMyAdmin

## Fichiers

- `docker-compose.yml`
- `.env.example`
- `init-mysql.sql`

## Variables

Les variables importantes sont déjà listées dans `.env.example`.

### MySQL

- `MYSQL_ROOT_PASSWORD`
- `MYSQL_DATABASE`
- `MYSQL_USER`
- `MYSQL_PASSWORD`
- `MYSQL_PORT`

### Keycloak

- `KEYCLOAK_DB_NAME`
- `KEYCLOAK_ADMIN`
- `KEYCLOAK_ADMIN_PASSWORD`
- `KEYCLOAK_PORT`
- `KEYCLOAK_INTERNAL_PORT`

## Lancement

```bash
docker compose --env-file .env.example up -d
```

## Arrêt

```bash
docker compose down
```

## Accès

- Keycloak: `http://localhost:8091`
- phpMyAdmin: `http://localhost:8088`

## Remarque

Le backend ne fait pas partie de cette stack:

- il tourne en local
- il utilise PostgreSQL local

## Images applicatives

Les images applicatives sont disponibles ici:

- `Backend/Dockerfile`
- `FrontEndF/Dockerfile`

Commandes rapides:

```bash
cd Backend
docker build -t pfe-backend .

cd ../FrontEndF
docker build -t pfe-frontend .
```
