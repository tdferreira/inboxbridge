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

### Une paire d'images pour les deux usages

Les **mêmes tags backend et frontend** couvrent les deux profils. Tailscale reste
facultatif : le second profil accepte un autre proxy HTTPS de confiance.

| Réglage du frontend | Liaison frontend/backend | HTTPS côté utilisateur |
| --- | --- | --- |
| `TLS_ENABLED=true` (défaut) | HTTPS vers le backend sur 8443 | Frontend sur 8443, avec les montages de certificats standard |
| `TLS_ENABLED=false` | HTTP vers le backend sur 8080 | Proxy HTTPS externe de confiance, par exemple Tailscale Serve |

En mode `false`, `PUBLIC_HOSTNAME` doit être le nom DNS canonique en minuscules,
sans schéma, port ni chemin. Le frontend sert HTTP sur 8080, refuse les autres
valeurs Host et remplace les en-têtes de transfert par l'origine HTTPS canonique.
Il fonctionne sous UID 101, y compris avec un système de fichiers en lecture seule
et `/tmp` inscriptible. Aucun montage de certificat n'est nécessaire dans ce mode.
Un mode invalide, des certificats absents en mode par défaut ou un nom mal formé
font échouer le démarrage. L'absence de certificat ne sélectionne jamais HTTP.

Sur un déploiement HTTP existant, conserver la base, les réseaux, dossiers, clés,
paramètres OAuth et cible Tailscale. Changer les deux images et ajouter seulement
`TLS_ENABLED: "false"` à l'environnement du service frontend. Garder les réglages
HTTP déjà présents sur le backend : `HTTP_PORT=8080`,
`QUARKUS_HTTP_INSECURE_REQUESTS=enabled`,
`QUARKUS_HTTP_SSL_CERTIFICATE_FILES` et `QUARKUS_HTTP_SSL_CERTIFICATE_KEY_FILES`
vides, ainsi que l'URL JDBC et les réglages de transfert existants. Le backend
utilise `CMD` : une commande Compose Java existante continue de fonctionner.
Le drapeau du frontend ne modifie pas implicitement le transport du backend ou
de la base.

Conserver l'entrée HTTP sur loopback, par exemple `127.0.0.1:18080:8080`, les ports
hôte du backend et de PostgreSQL non publiés et les réseaux Docker privés. Ce mode
suppose ces liaisons internes de confiance ; le TLS des boîtes source/destination
reste obligatoire. Les installations standard TLS conservent leurs certificats
et n'ont aucun nouveau réglage à appliquer. Ne pas lancer deux importeurs sur les
mêmes boîtes.
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
absence de ports hôte pour le backend et PostgreSQL. La même paire est aussi
essayée avec la commande Java et le transport HTTP existants, les services en
lecture seule, le refus des mauvais noms et la validation explicite du mode TLS,
sans montage de certificat. Les PR exécutent ces mêmes
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
