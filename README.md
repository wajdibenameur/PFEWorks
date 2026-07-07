# Plateforme intelligente de supervision centralisée et d'aide à la décision

![Status](https://img.shields.io/badge/status-demo%20acad%C3%A9mique-2e8b57)
![Backend](https://img.shields.io/badge/backend-Spring%20Boot-6db33f)
![Frontend](https://img.shields.io/badge/frontend-Angular-dd0031)
![Security](https://img.shields.io/badge/security-Keycloak%20%2B%20JWT-4b6cb7)
![Realtime](https://img.shields.io/badge/realtime-WebSocket%20%2F%20STOMP-ff9800)
![ML](https://img.shields.io/badge/ML-PyTorch%20%2B%20TorchScript-ee4c2c)

Solution de fin d'études réalisée pour la **Mediterranean School of Business (MSB)**.
Le projet vise à unifier la supervision d'une infrastructure hétérogène, à faciliter le traitement des incidents et à fournir une aide à la décision via un module de **prédiction ML de sévérité**.

> Projet académique réalisé dans le cadre d’un Projet de Fin d’Études.
> Certaines configurations sensibles, secrets, adresses internes et données réelles ne sont pas incluses dans ce dépôt.

## Statut du projet

- Projet de fin d'études
- Version de démonstration académique
- Dépôt pensé pour la présentation, la documentation et la valorisation GitHub

## Vue d'ensemble

La MSB exploite plusieurs solutions spécialisées de supervision et d'exploitation :

- `Zabbix` pour la supervision des serveurs et services
- `Observium` pour la supervision réseau dans l'existant
- `ZKBio` pour les accès biométriques
- des `caméras IP` pour la vidéosurveillance

Le problème principal n'était pas l'absence d'outils, mais leur **fragmentation**.  
Chaque plateforme a ses propres interfaces, ses propres formats de données et ses propres mécanismes d'accès, ce qui complique la supervision quotidienne, la corrélation des incidents et la prise de décision.

La solution développée propose une plateforme unique qui :

- centralise les données de supervision
- unifie les vues de monitoring
- gère les incidents et le ticketing
- diffuse les événements en temps réel
- applique des mécanismes de résilience
- intègre une prédiction ML de sévérité

## Architecture fonctionnelle

| Couche | Rôle principal |
|---|---|
| Frontend Angular | Interface utilisateur, dashboards, ticketing, chat, notifications et administration |
| Backend Spring Boot | API métier, orchestration, sécurité, agrégation, persistance et communication temps réel |
| Sécurité Keycloak / JWT / RBAC | Authentification centralisée, autorisation par rôles et contrôle d'accès |
| Intégration supervision | Connexion aux sources externes : Zabbix, SNMP, caméras IP |
| Résilience | `Retry`, `TimeLimiter`, `Fallback`, `Circuit Breaker` via Resilience4j |
| Machine Learning | Entraînement Python, export TorchScript et inférence côté backend via DJL |

## Responsabilités par couche

### 1. Frontend

Le frontend est développé avec **Angular**. Il est responsable de :

- l'affichage des tableaux de bord
- la consultation des incidents et tickets
- la collaboration temps réel via WebSocket/STOMP
- les notifications
- l'administration des utilisateurs et des rôles
- la consultation des données de supervision unifiées

### 2. Backend métier

Le backend est développé avec **Spring Boot 3** et constitue le cœur de la solution.  
Il est responsable de :

- l'exposition des API REST
- l'agrégation des données multi-sources
- la normalisation des données de monitoring
- la gestion des tickets et des incidents
- l'intégration de Zabbix, SNMP et caméras IP
- la publication d'événements temps réel
- la sécurisation des accès
- l'orchestration des mécanismes de résilience
- l'inférence du modèle ML

### 3. Sécurité et identité

La sécurité repose sur :

- `Keycloak` pour l'authentification centralisée
- `JWT` pour les sessions et les échanges sécurisés
- `RBAC` pour le contrôle d'accès basé sur les rôles
- `Spring Security` pour la protection des endpoints REST et WebSocket

### 4. Couche d'intégration

Cette couche isole les spécificités techniques des systèmes externes.

Elle permet de :

- récupérer les données de supervision
- gérer les différences de protocoles et de formats
- éviter le couplage direct entre le noyau métier et les outils tiers
- préparer des DTO homogènes pour le reste de l'application

Dans l'existant, `Observium` est conservé comme référence historique, mais la supervision réseau est reprise dans la solution finale par une **collecte SNMP native intégrée**.

### 5. Résilience

La plateforme intègre **Resilience4j** pour préserver la continuité de service dans les cas de dégradation ou d'indisponibilité partielle.

Les mécanismes utilisés sont :

- `Retry`
- `TimeLimiter`
- `Circuit Breaker`
- `Fallback`

### 6. Machine Learning

Le module ML est construit autour de :

- `PyTorch` pour l'entraînement
- `TorchScript` pour l'export du modèle
- `DJL` pour l'inférence dans Spring Boot

Objectif : produire une **prédiction ML de sévérité** à partir des données de supervision disponibles.

## Structure du dépôt

```text
.
├── Backend/        # API Spring Boot, sécurité, monitoring, ticketing, chat, ML inference
├── FrontEndF/      # Application Angular
├── PFERTC/         # Pipeline ML Python, artefacts et modèles TorchScript
├── docker/         # Services d'infrastructure : Keycloak, MySQL, phpMyAdmin
├── README.md       # Vue d'ensemble du projet
└── .github/        # Automatisation GitHub
```

## Modules principaux

### Backend

Le backend contient notamment :

- les contrôleurs REST
- les services de supervision
- les services SNMP
- les services Zabbix
- les services de ticketing
- la gestion des notifications
- la couche WebSocket/STOMP
- les règles de sécurité
- les mécanismes de résilience
- l'intégration ML

### Frontend

Le frontend contient :

- les pages dashboard
- les composants de supervision
- la gestion des utilisateurs
- l'authentification
- les vues temps réel
- les interfaces de ticketing et de collaboration

### PFERTC

`PFERTC` regroupe le pipeline ML :

- préparation des données
- feature engineering
- entraînement
- comparaison de modèles
- export vers TorchScript
- artefacts de validation

### Docker

`docker/` contient l'environnement d'infrastructure d'accompagnement :

- `MySQL` pour Keycloak
- `Keycloak`
- `phpMyAdmin`

## Sécurité et confidentialité

Ce dépôt ne contient pas :

- de mots de passe réels
- de secrets Keycloak
- de tokens Zabbix
- d'adresses IP internes sensibles
- de données de production

Les variables sensibles doivent être configurées localement à partir des fichiers `.env.example`.

## Prérequis

- Java 17
- Node.js 18+ ou 20+
- Maven Wrapper (`mvnw`)
- npm
- une base PostgreSQL accessible pour le backend
- Docker et Docker Compose pour Keycloak, MySQL et phpMyAdmin

## Démarrage rapide

### 1. Lancer l'infrastructure d'identité

```bash
cd docker
docker compose up -d
```

### 2. Configurer le backend

Créer les variables d'environnement à partir de `Backend/.env.example`.

Principales variables :

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `KEYCLOAK_BASE_URL`
- `KEYCLOAK_REALM`
- `KEYCLOAK_CLIENT_ID`
- `KEYCLOAK_CLIENT_SECRET`
- `ZABBIX_USERTOKEN`
- `SNMP_TOKEN`
- `ML_TORCHSCRIPT_ENABLED`

### 3. Démarrer le backend

```bash
cd Backend
./mvnw spring-boot:run
```

Le backend écoute par défaut sur le port `8099`.

### 4. Démarrer le frontend

```bash
cd FrontEndF
npm install
npm start
```

Le frontend Angular écoute par défaut sur le port `4200`.

## Ports par défaut

| Service | Port |
|---|---:|
| Frontend Angular | `4200` |
| Backend Spring Boot | `8099` |
| Keycloak | `8091` |
| MySQL Docker pour Keycloak | `3360` |

## Captures d'écran

Ajoute ici les captures les plus parlantes du projet.  
Pour une publication GitHub propre, je te conseille de copier les images du rapport vers un dossier du dépôt comme `docs/screenshots/` puis de les référencer ainsi :

### Vue d'architecture

![Architecture logique globale](docs/screenshots/global_architecture_logique.png)
![Architecture globale](docs/screenshots/global_architecture.png)

### Interfaces et démonstration

![Authentification](docs/screenshots/interface-authentication.png)
![Dashboard](docs/screenshots/interface-dashboard.png)
![Supervision SNMP](docs/screenshots/interface-snmp.png)
![Chat incident](docs/screenshots/interface-chat.png)
![Prédiction ML](docs/screenshots/interface-ml.png)

Si tu veux garder un README très clair, limite-toi à 4 ou 5 captures bien choisies plutôt qu'à une galerie trop longue.

## Qualité et tests

Le projet contient des tests ciblant :

- la sécurité
- les contrôleurs
- la supervision SNMP
- la résilience
- le ticketing
- le chat
- le module ML

## Points forts

- supervision centralisée
- architecture modulaire
- sécurité centralisée avec Keycloak
- communication temps réel
- ticketing intégré
- résilience applicative
- aide à la décision par ML

## Remarques d'architecture

- `Observium` est présent dans l'existant de la MSB, mais la solution finale privilégie une collecte `SNMP` native intégrée.
- `ZKBio` est conservé comme solution métier existante pour les accès biométriques.
- Le module ML n'est pas présenté comme une IA générique, mais comme une **prédiction ML de sévérité**.

## Auteur

**Wajdi Ben Ameur**  
Projet de fin d'études - MSB  
Spécialité : Génie Logiciel et Systèmes d'Information
