# Déployer HS Code Matcher API sur Azure

Application Spring Boot 17 (`hscode-matcher-api`). Deux approches courantes : **Azure App Service** (JAR ou conteneur) ou **Azure Container Apps**.

## Prérequis données

1. **Fichiers CSV nomenclature** (UTF-8), un par langue chargée. En production, les placer sur un volume persistant (ex. **Azure Files** monté sur le conteneur ou l’App Service en `/home/data/nomenclature/`).
2. **Modèle ONNX** (optionnel mais requis si `hs.matcher.onnx.enabled=true`) : le fichier `*.onnx` n’est pas versionné (voir `.gitignore`). Soit vous le copiez dans `src/main/resources/onnx/` avant `mvn package` / build Docker, soit vous pointez `HS_MATCHER_ONNX_MODEL_PATH` vers un chemin fichier accessible (montage Azure Files), par ex. `file:/home/data/onnx/model_qint8_avx512.onnx`.
3. **Cache embeddings** : pour réutiliser les matrices entre redémarrages, monter un disque persistant et définir `HS_MATCHER_EMBEDDING_CACHE_DIR` (ex. `/home/data/embedding-cache`).

## Variables d’environnement (Spring relaxed binding)

| Variable | Propriété équivalente | Exemple |
|----------|------------------------|---------|
| `SPRING_PROFILES_ACTIVE` | — | `azure` (recommandé) |
| `NOMENCLATURE_CSV_EN` | `nomenclature.csv.en` | `file:/home/data/nomenclature/nomenclature-en.csv` |
| `NOMENCLATURE_CSV_FR` | `nomenclature.csv.fr` | `file:/home/data/nomenclature/nomenclature-fr.csv` |
| `NOMENCLATURE_CSV_DE` | `nomenclature.csv.de` | `file:/home/data/nomenclature/nomenclature-de.csv` |
| `HS_MATCHER_ONNX_ENABLED` | `hs.matcher.onnx.enabled` | `true` / `false` |
| `HS_MATCHER_ONNX_MODEL_PATH` | `hs.matcher.onnx.model-path` | `file:/home/data/onnx/model_qint8_avx512.onnx` |
| `HS_MATCHER_EMBEDDING_CACHE_DIR` | `hs.matcher.embedding.cache-dir` | `/home/data/embedding-cache` |
| `NOMENCLATURE_ADMIN_RELOAD_TOKEN` | `nomenclature.admin.reload-token` | (secret pour `POST /api/v1/admin/reload`) |
| `JAVA_OPTS` | — | `-Xms512m -Xmx1536m` (ajuster selon la SKU) |

La plateforme peut définir **`PORT`** ; le profil `azure` utilise `server.port=${PORT:8080}`.

Santé : `GET /actuator/health` (configurer la sonde App Service sur ce chemin si besoin).

---

## Option A — Azure App Service (Java 17, JAR)

1. Build local : `mvn -B -DskipTests package` (depuis `hscode-matcher-api/`).
2. Créer une Web App **Linux**, stack **Java 17**.
3. **Configuration** → **Paramètres d’application** : ajouter `SPRING_PROFILES_ACTIVE=azure` et les variables CSV/ONNX ci-dessus.
4. **Configuration générale** → **Commande de démarrage** (si nécessaire) :

   ```bash
   java -Dspring.profiles.active=azure -jar /home/site/wwwroot/app.jar
   ```

5. Déployer le JAR `target/hscode-matcher-api-*.jar` renommé en `app.jar` dans `wwwroot`, ou utiliser le [GitHub Action](#github-actions) / `az webapp deploy`.

**Montage Azure Files (CSV / ONNX)** : dans App Service, « Montage de stockage » → monter un partage sur un chemin (ex. `/home/data`) puis utiliser des `file:/home/data/...` dans les variables ci-dessus.

---

## Option B — Conteneur (Dockerfile fourni)

Depuis `hscode-matcher-api/` :

```bash
docker build -t hscode-matcher-api:local .
```

Pousser vers **Azure Container Registry** puis créer une **Web App for Containers** ou **Container Apps** en pointant l’image, port **8080**, variables d’environnement comme ci-dessus.

> Si le build Docker s’exécute sans fichier `.onnx` dans le contexte, le JAR n’inclura pas le modèle : désactiver ONNX (`HS_MATCHER_ONNX_ENABLED=false`) ou copier le modèle dans `src/main/resources/onnx/` avant `docker build`, ou monter le modèle via volume et `HS_MATCHER_ONNX_MODEL_PATH`.

---

## GitHub Actions (déploiement JAR vers App Service)

Fichier exemple : `.github/workflows/azure-webapp-hscode-matcher-api.yml`. À configurer dans le dépôt :

- Secrets : `AZURE_WEBAPP_NAME`, `AZURE_WEBAPP_PUBLISH_PROFILE` (téléchargeable depuis le portail Azure → Web App → **Get publish profile**).

Adapter `paths` / déclencheurs selon votre branche.

---

## Références

- [Azure App Service — Java](https://learn.microsoft.com/azure/app-service/quickstart-java)
- [Configurer une application Java sur App Service](https://learn.microsoft.com/azure/app-service/configure-language-java-deploy-run)
- [Montage Azure Files sur App Service](https://learn.microsoft.com/azure/app-service/configure-connect-to-azure-storage)
