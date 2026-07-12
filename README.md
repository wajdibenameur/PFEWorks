# PFEPROJECT

## Architecture locale

- Backend Spring Boot: en local
- Frontend Angular: en local
- PostgreSQL: en local pour le backend
- Keycloak + MySQL: dans Docker

## Ports par défaut

- Backend: `http://localhost:8099`
- Frontend: `http://localhost:4200`
- Keycloak: `http://localhost:8091`
- MySQL Docker: `localhost:3360`
- phpMyAdmin: `http://localhost:8088`

## Prérequis

- Java 17
- Node.js 20
- Docker Desktop
- PostgreSQL local accessible sur `localhost:5432`

## Base de données PostgreSQL locale

Le backend utilise PostgreSQL en local avec les valeurs par défaut suivantes:

- base URL: `jdbc:postgresql://localhost:5432/monotoring`
- utilisateur: `pfeuser`
- mot de passe: `pg14789`

Tu peux les surcharger avec:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

## Infra Docker

L’infra Docker du projet sert à lancer:

- MySQL pour Keycloak
- Keycloak
- phpMyAdmin

Lancement:

```bash
cd docker
docker compose --env-file .env.example up -d
```

Arrêt:

```bash
cd docker
docker compose down
```

## Lancer le backend

Avant de démarrer le backend, vérifie que PostgreSQL local est bien actif.

Exemples de variables utiles:

- `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/monotoring`
- `SPRING_DATASOURCE_USERNAME=pfeuser`
- `SPRING_DATASOURCE_PASSWORD=pg14789`
- `KEYCLOAK_BASE_URL=http://localhost:8091`
- `KEYCLOAK_REALM=my-realm`
- `APP_CORS_ALLOWED_ORIGINS=http://localhost:4200`

Démarrage:

```bash
cd Backend
./mvnw spring-boot:run
```

## Lancer le frontend

Le frontend pointe par défaut vers le backend local.

Démarrage:

```bash
cd FrontEndF
npm ci
npm start
```

## CI / SonarCloud

Le dépôt contient maintenant:

- un workflow CI build/test dans `.github/workflows/ci.yml`
- un workflow SonarCloud dans `.github/workflows/sonarcloud.yml`

Secrets GitHub utilisés pour SonarCloud:

- `PFEWORKS_GITHUB_TOKEN`
- `SONAR_PROJECT_KEY`
- `SONAR_ORGANIZATION`

## Documentation DevOps

- [Architecture](docs/architecture.md)
- [Plan de déploiement](docs/devops-deployment.md)
- [Roadmap de déploiement](docs/deployment-roadmap.md)
- [Conteneurisation backend](docs/backend-containerization.md)
- [Infra Docker](docker/README.md)
