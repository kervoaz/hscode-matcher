# HSCODE — Nomenclature douanière & HS Code Matcher

Fichiers nomenclature **Harmonized System (HS)** multilingues et API **Spring Boot** pour la recherche libellé → code HS (hybride **Lucene + ONNX**).

## Contenu du dépôt

| Élément | Description |
|---------|-------------|
| `Nomenclature{FR,EN,DE}.XLSX` | Exports officiels (UE / CIRCABC), trois langues |
| `hscode-matcher-api/` | Service Java 17 — REST, index Lucene, embeddings ONNX optionnels, UI lab statique |
| `.planning/` | Produit, roadmap, architecture, pitch technique |
| `.github/workflows/` | CI optionnelle — déploiement Azure Web App |

## Source des données

Les tableaux Excel proviennent de la plateforme européenne **[CIRCABC](https://circabc.europa.eu)** (publication Commission européenne). L’API consomme des **CSV UTF-8** dérivés de ces exports (pas de lecture XLSX au démarrage).

## Démarrage rapide (API)

Prérequis : **JDK 17+**, Maven ou wrapper inclus dans `hscode-matcher-api/`.

```bash
cd hscode-matcher-api
./mvnw test
```

Lancer avec les CSV au niveau du dépôt (profil `dev`) :

```bash
./mvnw spring-boot:run -Pdev
```

Sous **PowerShell**, préférer `-Pdev` plutôt qu’un profil non quoté sur la ligne de commande.

- **UI de test** : [http://localhost:8080/](http://localhost:8080/)  
- **Santé** : `GET /actuator/health`  
- **Recherche** : `GET /api/v1/search?q=...&lang=FR|EN|DE&limit=10`

Export **XLSX → CSV** (exemple depuis la racine du dépôt) :

```bash
mvn -q -f hscode-matcher-api/pom.xml exec:java -Dexec.in=NomenclatureFR.XLSX -Dexec.out=nomenclature-fr.csv
```

(Répéter pour EN/DE. Ne pas utiliser de `-Dexec.args=...` non quoté sous PowerShell.)

## Configuration utile

Les chemins CSV et le modèle ONNX se règlent dans `hscode-matcher-api/src/main/resources/application.properties` (ou variables d’environnement / profils `dev`, `azure`). Le fichier **`.onnx`** n’est pas versionné par défaut : voir `CLAUDE.md` et le dépôt [Hugging Face — MiniLM ONNX](https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/tree/main/onnx).

## Déploiement Azure

Voir **`hscode-matcher-api/DEPLOY-AZURE.md`** (Dockerfile, profil `azure`, variables d’environnement, montage de fichiers).

## Documentation

- **`CLAUDE.md`** — repères pour agents / développeurs (build, API, contraintes PowerShell)  
- **`.planning/PROJECT.md`** — périmètre produit  
- **`.planning/PITCH_TECHNIQUE.md`** — pitch technique (valeur, stack)  
- **`.planning/research/ARCHITECTURE.md`** — architecture détaillée (dont schémas Lucene + ONNX)  
- **`.planning/ROADMAP.md`** — jalons

## Licence / usage

Respecter les conditions d’utilisation des données publiées sur CIRCABC.
