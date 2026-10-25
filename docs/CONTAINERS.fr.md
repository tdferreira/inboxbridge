# Images préconstruites et publication des releases

[English](CONTAINERS.md)

## Déploiement

Chaque release produit deux images GHCR, backend et frontend, pour `linux/amd64`
et `linux/arm64`. La machine de déploiement ne compile rien. Les images officielles
sont `ghcr.io/tdferreira/inboxbridge-backend` et `ghcr.io/tdferreira/inboxbridge-frontend`.
Sur un fork, le workflow utilise automatiquement son propriétaire et son nom de
dépôt : `ghcr.io/OWNER/REPOSITORY-backend` et `...-frontend`, en minuscules.
Utiliser la même version pour les deux services, ou leurs digests publiés.

Télécharger l'archive de release et préparer la configuration décrite dans
[SETUP.md](SETUP.md). Définir les références exactes de la version publiée :

```text
INBOXBRIDGE_BACKEND_IMAGE=ghcr.io/OWNER/REPOSITORY-backend:vX.Y.Z
INBOXBRIDGE_FRONTEND_IMAGE=ghcr.io/OWNER/REPOSITORY-frontend:vX.Y.Z
```

Ces références ne sont pas des secrets. Démarrer ensuite :

```sh
docker compose -f docker-compose.yml -f docker-compose.images.yml pull
docker compose -f docker-compose.yml -f docker-compose.images.yml up -d --no-build
```

Le complément conserve le TLS standard, les ports privés du backend et de la base,
les certificats propres à chaque service et le stockage PostgreSQL. `--no-build`
évite la compilation malgré les sections de construction du fichier de base.
Avant une mise à jour, sauvegarder la base et conserver les clés et paramètres de
coffre. V74/V75 ajoutent des réglages IDLE ; un retour arrière nécessite aussi la
sauvegarde de base antérieure. Ne pas copier à chaud un dossier PostgreSQL actif.

Tailscale reste facultatif. Il peut restreindre l'accès et terminer HTTPS côté
externe ; ces images attendent toujours le TLS interne décrit dans SETUP.md.
Un déploiement utilisant un autre packaging HTTP doit préparer sa migration de
proxy et de certificats avant d'adopter ces images. Ne pas remplacer les images
sans vérifier cette compatibilité ni lancer deux importeurs sur les mêmes boîtes.
Aucun identifiant Gmail, fichier d'environnement, certificat privé, contenu de base
ou clé de chiffrement ne doit entrer dans les images ou leur contexte de construction.

## Publication par le mainteneur

Un tag de version (`vX.Y.Z`, ou `vX.Y.Z-rc.1`) lance la release. Un lancement manuel
valide le tag demandé et extrait exactement son code, plutôt que celui de la
branche depuis laquelle le workflow a été lancé.

Le workflow valide le backend et son exécutable, l'interface, les extensions et les
contrôles de confidentialité. Le contexte Docker contient seulement les résultats
de compilation, la configuration Nginx et la licence. Avant toute publication,
les images sont construites et essayées sur une stack jetable : interface/API en
HTTPS vérifié, utilisateurs sans privilèges, isolation de la clé d'autorité et
absence de ports hôte pour le backend et PostgreSQL. Les PR exécutent ces mêmes
contrôles sans connexion au registre ni publication.

Après validation, les deux architectures sont publiées avec leurs métadonnées de
source, attestations de provenance et SBOM. Les tags sont la version complète et
`sha-COMMIT_SOURCE_COMPLET`, sans alias mobile `latest`. La release GitHub n'est
publiée qu'après réussite des deux constructions/publications. L'opération n'est
pas atomique entre les deux paquets : si la seconde échoue, la première peut être
présente. Relancer alors le workflow et attendre la release terminée avant usage.
L'archive et les notes de release incluent les instructions et le complément Compose.

Le job utilise `GITHUB_TOKEN` avec `contents: write` et `packages: write`, sans jeton
personnel. Une politique d'organisation peut interdire la création de paquets.
Lors de la première publication, vérifier l'accès et rendre les deux paquets
**publics** si un téléchargement anonyme est souhaité : un dépôt public ne garantit
pas cette visibilité. Pour un paquet existant, autoriser le dépôt à publier via
Actions. Les étiquettes de source identifient le dépôt, y compris sur un fork.
La publication depuis un fork ne dépend pas de l'acceptation de la contribution.
Ne pas réaffecter un tag à un autre code ; préférer les digests pour figer un déploiement.
