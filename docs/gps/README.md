# GPS-001 — Remplacement du suivi GPS

Date : 24 octobre 2026. Statut : architecture acceptée ; premier lot de code Android livré pour revue, validation native et terrain restante.

## Besoin et décisions acquises

- Remplacer le SDK GPS commercial de Transistorsoft.
- Android uniquement ; iOS ne fait pas partie de la roadmap.
- Après redémarrage du téléphone, le chauffeur peut rouvrir l’application et se remettre en ligne.
- Produire des changements traçables, accompagnés de documents et de preuves de validation.
- Traiter la nouvelle authentification dans un chantier séparé. Les OTP fixes de PoC ne sont pas une solution de production.

## Documents

1. [ADR-001 : comparaison et recommandation](adr-001-choix-du-moteur.md).
2. [Plan de livraison et recette](plan-de-livraison.md).
3. [Livraison GPS-001.1 et limites](livraison-gps-001-1.md).

## État de référence inspecté

| Dépôt | Révision |
| --- | --- |
| navigator-app-pp | `0da77ce050ad4621efc0ede115c6e2692cda2c8e` |
| fleetbase-pp | `9c5e164e5476b6038d24653f6e557b43aa27959c` |
| sous-module FleetOps | `33032e53ebee5e4a514970ee2379c12348c77c54` |

Navigator déclare React Native 0.86.0, React 19.2.7, la nouvelle architecture et Hermes. Android cible l’API 36 et déclare un minimum API 24. Ces déclarations ne constituent pas une validation de compilation locale.

## Constats dans le code

| Emplacement relatif à Navigator | Responsabilité actuelle / impact |
| --- | --- |
| `src/contexts/LocationContext.tsx` | Démarrage selon chauffeur, statut en ligne et permission ; distance de 10 m ; envoi HTTP natif avec Bearer ; configuration de reprise au démarrage et après terminaison. |
| `src/utils/location.js` | Positions ponctuelles utilisées par les écrans et le géocodage. À migrer aussi. |
| `src/contexts/AuthContext.tsx` | Autre chemin d’envoi via `driver.track()`. Unifier le transport pour éviter les doublons. |
| `src/screens/LocationPermissionScreen.tsx` | Demande de position précise Android ; revoir le parcours de permission avec le service. |
| `__tests__/LocationTrackingLifecycle-test.js` | Tests existants de démarrage/arrêt avec mocks. À préserver en termes de comportement, sans conserver le couplage au SDK. |
| `android/build.gradle`, `android/app/build.gradle`, manifeste Android | Dépendances et configuration Transistorsoft, dont clé de licence. |
| `package.json`, `yarn.lock`, `.github/workflows/react-native-ci.yml` | Dépendances et références aux secrets de licence. |

`react-native-background-fetch` est utilisé dans le contexte GPS pour des demandes périodiques. Son retrait est proposé si aucun autre usage n’apparaît pendant l’implémentation ; ne pas déduire sa licence de celle du SDK de géolocalisation.

Le contrôleur `packages/fleetops/server/src/Http/Controllers/Api/v1/DriverController.php`, méthode `track()`, exploite latitude, longitude, altitude, heading et speed. Dans la méthode examinée, aucun traitement explicite du timestamp client ni d’une clé d’idempotence n’a été trouvé. Un replay hors réseau nécessite donc un contrat serveur vérifié ; l’ordre de réception ne doit pas faire reculer la position courante ni déclencher des événements métier historiques comme s’ils étaient actuels.

## Journal de livraison

| Date | Livraison | État | Vérification |
| --- | --- | --- | --- |
| 2026-10-24 | Analyse, ADR proposé et plan de recette GPS-001 | Documents locaux sur `docs/gps-migration-proposal` | Lecture du code et des sources officielles ; aucun build ni test matériel effectué |

| 2026-10-24 | GPS-001.1 : service Kotlin et intégration Navigator | Branche `feat/android-native-location`, code pour revue | 14 tests JS réussis ; codegen réussi ; compilation native et terrain non validés |

Les liens GitHub de PR, commits de livraison, APK et résultats de tests seront ajoutés lorsqu’ils existent. Aucun résultat de test ou déploiement ne doit être déclaré sur la base de ce dossier de conception.
