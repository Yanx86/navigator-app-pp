# GPS-001.1 — Première intégration native Android

Date : 24 octobre 2026. Branche : `feat/android-native-location`.
Statut : code à relire et à compiler sur un environnement Android configuré. Non validé pour production.

## Livré

- TurboModule Kotlin `NativeDriverLocation`, spécification TypeScript et enregistrement dans l’application.
- Service Android de premier plan `location`, notification persistante en français/anglais, Fused Location Provider 21.0.1 (version déjà déclarée dans le projet de référence).
- Acquisition indépendante du runtime JavaScript : intervalle demandé 10 secondes, minimum 5 secondes et distance minimale 10 mètres. Ces paramètres ne garantissent pas une cadence système exacte.
- Rejet des positions de plus de 30 secondes ou de précision supérieure à 100 mètres. Champs vitesse, altitude et direction absents conservés comme null.
- Envoi natif HTTP unique des positions courantes ; absence de redirection HTTP avec le Bearer ; HTTPS imposé en release, HTTP autorisé en debug seulement.
- Jeton natif conservé en mémoire seulement, invalidation des callbacks par génération de session ; arrêt en fin de service, déconnexion et changement d’organisation.
- Suspension sur réponse 401/403 ou rejet permanent de l’endpoint ; état réel et dernière date d’envoi exposés au tableau de bord.
- Suppression des deux dépendances Transistorsoft du package et du lock Yarn, de la configuration native Android et des secrets de licence référencés en CI.
- Job iOS de la CI désactivé : iOS reste hors périmètre ; ses anciens fichiers et Podfile.lock ne constituent pas une version supportée.
- Correction du nom de configuration ESLint : l’ancienne configuration référençait un package absent ; utilisation du package `@react-native/eslint-config` déjà déclaré.

## Limites explicites

Ce lot réalise un suivi en direct, sans file durable ni replay des trajets hors réseau. Les points dont l’envoi échoue ou arrive pendant une requête en cours sont abandonnés ; un prochain point récent pourra être envoyé. Le tableau de bord le signale. Le lot GPS-001.2/3 reste nécessaire pour la conservation des trajets et la réception idempotente côté FleetOps.

Pas de reprise automatique après reboot ou destruction du processus : `START_NOT_STICKY`, pas de récepteur de boot GPS, pas de jeton natif persistant. Le chauffeur doit rouvrir l’app. Aucun engagement de survie à un arrêt forcé ni de comportement identique entre fabricants.

L’arrêt tente aussi d’annuler la requête en cours ; une requête déjà acceptée par le serveur ne peut être annulée rétroactivement. La nouvelle authentification AUTH-001 reste à réaliser. Les mécanismes existants de connexion et de stockage de session n’ont pas été remplacés par cette livraison.

Ni autonomie, ni précision terrain, ni tenue écran éteint ne sont encore mesurées. Ne pas fusionner comme remplacement GPS de production sans recette native et terrain.

## Vérifications

| Vérification | Résultat |
| --- | --- |
| Installation Yarn 3.6.4 et mise à jour du lock | Réussie, avertissements de dépendances préexistants |
| Suite Jest | 5 suites, 14 tests réussis lors de la première exécution hors bac à sable |
| Codegen React Native Android | Réussi ; génération seule, ne prouve pas la compilation Kotlin |
| ESLint ciblé sur spécification, service TS, contexte et tests | 0 erreur, 6 avertissements `no-void` |
| Tests Kotlin `TrackingSessionTest` | Écrits, exécution non validée |
| Compilation Android | Tentée avec Gradle 8.13/Java 17 ; aucun résultat final exploitable récupéré. SDK Android absent des emplacements standard et variables SDK non configurées sur ce poste |
| APK release, appareil physique et batterie | Non exécutés |

## Reprise sur la machine Android de développement

1. Récupérer la branche du fork et installer les dépendances avec `corepack yarn@3.6.4 install --immutable`.
2. Configurer le SDK/NDK attendus par le projet, `.env`, Firebase et la signature selon la documentation existante. Garder les secrets hors Git.
3. Exécuter `yarn test --ci --runInBand --no-watchman`.
4. Dans `android`, exécuter `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`, puis `./gradlew :app:assembleRelease` avec la signature configurée. Sous Windows, utiliser `gradlew.bat`.
5. Vérifier dans l’APK/manifeste fusionné l’absence des services Transistorsoft et la présence du service `DriverLocationService` non exporté.
6. Sur téléphone avec Google Play Services, vérifier permission précise, mise en ligne, notification, réception Fleetbase, écran éteint, coupure réseau, expiration de session, changement de chauffeur et arrêt local même si le serveur est inaccessible.

Enregistrer dans ce document ou dans la PR les versions de téléphone/Android, le SHA testé et les résultats effectifs. La CI du dépôt s’exécute sur PR vers main ; un push de cette branche seule ne suffit pas à déclencher ce workflow.
