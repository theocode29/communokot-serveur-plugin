# CommunokotProgress

Plugin Paper 1.21.x qui:
- compte les `STONE` cassées (`BlockBreakEvent`),
- garde la progression en mémoire,
- persiste dans SQLite,
- génère un snapshot JSON local,
- envoie un `repository_dispatch` vers GitHub (traité par Actions).

## Stack
- Java 21+
- Maven
- Paper API `1.21.11-R0.1-SNAPSHOT`

## Build
```bash
mvn package
```

Puis copier le JAR dans `plugins/` du serveur Paper.

## Configuration
Éditer `src/main/resources/config.yml` (ou le fichier généré dans le dossier plugin au runtime):
- `database.path`
- `snapshot.localPath`
- `export.snapshotIntervalSeconds`
- `github.*`

Token GitHub:
- recommandé: variable d’environnement `COMMUNOKOT_GITHUB_TOKEN`
- alternative: `github.token` dans la config

Le token doit avoir au minimum les permissions `Contents: Write` sur le repo cible.

## Commandes
- `/communokot export` : force un cycle snapshot + flush SQLite + dispatch GitHub
- `/communokot reload` : recharge la config runtime (DB path nécessite restart)

Permission:
- `communokot.admin`

## Workflow GitHub
Le workflow `.github/workflows/stone-sync.yml` doit être présent sur le repo qui reçoit les dispatch.

Le plugin envoie un payload base64 (ou gzip+base64), le workflow:
1. décode,
2. écrit `target_path`,
3. commit/push sur `target_branch`.

## Notes
- `snapshotIntervalSeconds` et `dispatchIntervalSeconds` s’exécutent hors thread principal.
- En cas d’échec GitHub, le snapshot local et SQLite restent la source de vérité.
