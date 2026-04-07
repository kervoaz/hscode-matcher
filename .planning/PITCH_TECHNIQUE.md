# HS Code Matcher — pitch technique

*Une phrase pour l’ascenseur :* **on transforme du langage naturel (souvent bancal) en codes douaniers HS fiables, en français, anglais ou allemand — sans appeler le cloud, sans Elasticsearch, avec une recherche hybride qui marie le meilleur du texte et du sens.**

---

## Le problème (sans le sucre)

Les utilisateurs ne tapent pas « Chapitre 87 — Véhicules automobiles autres que ferroviaires ». Ils écrivent *« pièces auto »*, *« voiture* avec faute*, *« camionnette livraison »*. La nomenclature officielle, elle, parle le langage du droit et de la logistique. **Le gouffre entre l’intention et le libellé réglementaire** est là que les mauvais classements douaniers se glissent — retards, rectifications, friction UX.

## La réponse (architecture qui claque)

Ce projet est une **API Spring Boot** qui expose une recherche **hybride** sur la nomenclature **HS à 6 chiffres** (données UE / CIRCABC, trois langues).

| Voie | Moteur | Ce qu’elle sait faire |
|------|--------|------------------------|
| **Lexicale** | **Apache Lucene** (BM25 + fuzzy) | Typos, fragments de texte, mots-clés proches du libellé officiel |
| **Sémantique** | **ONNX** (embeddings multilingues locaux) | Synonymes, reformulations, « même idée, autres mots » |
| **Fusion** | **RRF** (reciprocal rank fusion) | Deux classements → un seul top-N robuste, sans bricoler des scores incomparables |

Pas de magie noire : **RRF** évite de mélanger brutalement un score BM25 et un cosinus comme si c’était la même échelle. On fusionne des **rangs** — pattern reconnu, simple, efficace à l’échelle nomenclature.

## Pourquoi c’est « sexy » côté engineering

1. **Souveraineté runtime** — tout tourne **dans le JAR** : pas d’appel OpenAI, pas de vector DB managée obligatoire. Le modèle d’embedding est **local** (ONNX) ; les index vivent **en mémoire** (Lucene + matrices), rechargés depuis **CSV** quand la nomenclature change.

2. **Triptyque prod-ready** — **Actuator** (health, métriques), **reload** atomique sans redémarrage, **corrélation** (`X-Request-Id`), options de **lab** (explain, hybrid on/off, seuils) pour régler le comportement sans deviner.

3. **Multilingue natif** — FR / EN / DE : analyzers Lucene **par langue**, modèle d’embedding **multilingue** — même requête conceptuelle, même stack.

4. **Déployable partout** — JAR classique, **Dockerfile**, profil **Azure**, UI statique de test : du laptop à l’App Service sans réécrire le cœur.

## Stack en une ligne

**Java 17 · Spring Boot 3 · Lucene 9 · ONNX Runtime · RRF** — le minimum viable pour du **full-text sérieux** + du **sémantique offline**, avec une courbe d’exploitation raisonnable.

## En résumé pour un slide

> **Hybrid search douanier offline**  
> Lucene pour le texte réel. ONNX pour le sens. RRF pour l’ordre final.  
> Trois langues. Un reload. Zéro dépendance SaaS à la requête.

---

## Phrases d’accroche (au choix)

- *« On ne classe pas des chaînes — on aligne l’intention utilisateur sur la loi du HS. »*
- *« Deux moteurs, une liste : le fuzzy attrape la faute, l’embedding attrape le synonyme. »*
- *« La nomenclature UE, servie comme un moteur de recherche moderne — sans vendre vos requêtes à un tiers. »*

---

*Document vivant — à aligner avec `.planning/PROJECT.md` et `.planning/research/ARCHITECTURE.md`.*
