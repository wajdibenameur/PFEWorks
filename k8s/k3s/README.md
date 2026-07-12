# Deploiement k3s

Ce dossier contient le deploiement complet de PFEPROJECT pour la VM cible.

## Ce que le cluster deploie

- Frontend Angular
- Backend Spring Boot
- PostgreSQL pour le backend
- Keycloak
- MySQL pour Keycloak

## Images attendues

Les manifests utilisent ces tags locaux:

- `pfe-backend:latest`
- `pfe-frontend:latest`

Construis-les depuis la racine du depot:

```bash
cd Backend
docker build -t pfe-backend:latest .

cd ../FrontEndF
docker build -t pfe-frontend:latest .
```

Si le noeud k3s ne voit pas les images locales, importe-les dans le runtime du cluster ou pousse-les dans ton registry.

## Noms d'hote

L'ingress utilise par defaut:

- `app.pfeworks.local`
- `api.pfeworks.local`
- `keycloak.pfeworks.local`

Ajoute ces noms dans le fichier hosts de la VM ou de ta machine de dev et pointe-les vers l'IP de la VM.

## Application

```bash
kubectl apply -k k8s/k3s
```

## Acces

- Frontend: `http://app.pfeworks.local`
- API backend: `http://api.pfeworks.local`
- Keycloak: `http://keycloak.pfeworks.local`

## Notes

- Le backend utilise PostgreSQL dans le cluster.
- Le build frontend de production pointe vers `api.pfeworks.local`.
- L'import du realm Keycloak contient des roles et utilisateurs de demonstration.
- Pour HTTPS, ajoute un gestionnaire de certificats ou une terminaison TLS devant l'ingress.
