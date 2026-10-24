# ADR-001 — Moteur GPS Android

Date : 24 octobre 2026. Statut : choix Kotlin + Fused Location Provider accepté par le porteur du projet le 24 octobre 2026.

## Recommandation

Créer un module Android Kotlin limité au suivi, exposé à React Native via une interface typée compatible avec sa nouvelle architecture. Utiliser un service de premier plan de type `location` avec notification persistante et le Fused Location Provider de Google Play Services.

Le choix privilégie le périmètre Android seul, le contrôle du cycle de vie et l’absence du SDK commercial Transistorsoft. Il implique que nous maintenions le service, le stockage durable et les reprises réseau. Ce n’est pas un simple remplacement de dépendance ni une solution sans coût de maintenance.

Hypothèse à vérifier : les téléphones chauffeurs disposent de Google Play Services. Sinon, évaluer un fournisseur Android `LocationManager` derrière la même interface ; ne pas promettre un support sans GMS non testé. Google Play Services reste une dépendance de plateforme : cette proposition ne prétend pas que toute la pile est libre.

## Options comparées

| Option | Avantages | Coût et limites | Avis |
| --- | --- | --- | --- |
| Module Kotlin + Fused Location Provider + service Android | Contrôle du stockage et du cycle de vie ; intégration ciblée Android ; aucun SDK Transistorsoft | Développement et maintenance natifs ; tests fabricants indispensables | Recommandé pour ce périmètre |
| `expo-location` + `expo-task-manager` | Modules Expo sous licence MIT ; suivi de fond disponible ; intégration possible dans un projet React Native existant | Ajout de l’infrastructure Expo ; file hors réseau et transport métier à réaliser ; validation du build requise | Alternative si la réduction du code natif prime |
| `@react-native-community/geolocation` | MIT ; positions ponctuelles ; prise en charge annoncée de TurboModules | Ne constitue pas à lui seul notre service durable, notre file réseau et notre gestion de session | Brique possible, pas un remplacement complet |
| `@mauron85/react-native-background-geolocation` | Apache-2.0 ; fonctionnalités de suivi et d’envoi HTTP | Documentation Android fondée sur d’anciens niveaux SDK ; compatibilité RN 0.86/API 36 non établie ici | Non retenu sans audit supplémentaire |

La documentation Expo consultée couvre l’installation dans RN 0.86 : l’alternative est crédible, mais aucun essai n’a été réalisé dans Navigator. Nous n’avons pas établi que Mauron85 est archivé ; le rejet est une appréciation du risque d’intégration, pas une affirmation sur son statut.

## Architecture proposée

Flux : interface chauffeur → `LocationTrackingService` TypeScript → module Kotlin → acquisition GPS → file durable → transport Fleetbase.

- Interface TypeScript : démarrer une session, l’arrêter, obtenir une position ponctuelle, observer l’état et les dernières positions. Les écrans ne connaissent pas le fournisseur GPS.
- Le service démarre lors de la mise en ligne explicite, depuis une activité visible, après vérification des permissions. Notification visible pendant le suivi.
- La collecte et l’envoi ne dépendent pas du montage d’un composant React ou d’un timer JavaScript.
- État natif explicite : arrêté, démarrage, suivi, permission indisponible, session expirée, erreur. L’interface ne prétend pas suivre si le service n’a pas démarré.
- File transactionnelle persistante, proposée avec Room/SQLite. Données minimales : identifiant de point, chauffeur, instance, génération de session, coordonnées, précision et heure de mesure. Pas de jeton dans chaque point.
- Transport unique avec timeout, retries bornés et attente croissante pour erreurs temporaires. Ne pas réessayer indéfiniment une requête rejetée comme invalide.
- Jeton fourni par un composant de session sécurisé ; ne pas intégrer d’OTP fixe, de clé API globale faisant office d’identité chauffeur ou de jeton dans les logs.
- Sur 401/403 : suspendre les envois et la collecte de cette session, notifier l’interface ; pas de boucle de réauthentification GPS. Politique précise de renouvellement à définir dans AUTH-001.
- À la déconnexion ou au changement de chauffeur/instance : arrêter, invalider la génération de session, annuler les travaux en attente et purger les points associés. Une requête déjà reçue par le serveur ne peut pas être retirée rétroactivement.

La notion « chauffeur hors ligne » est distincte d’une panne réseau : une panne conserve les points de la session autorisée ; la fin de service arrête la collecte. La politique proposée purge les points non envoyés à la fin de service pour éviter des envois après cette fin ; cette perte possible doit être affichée/documentée et validée métier.

## Limites et décisions de conception

La notification de premier plan permet le suivi écran éteint, sous réserve des contraintes système. Aucun engagement de survie à un arrêt forcé par l’utilisateur. Après reboot, pas de redémarrage automatique demandé : reprise explicite par le chauffeur. Le retrait de l’app des applications récentes doit être testé séparément de l’arrêt forcé, selon les fabricants.

Ne pas demander par défaut `ACCESS_BACKGROUND_LOCATION` : déterminer les permissions réellement nécessaires au service lancé depuis l’activité visible, puis vérifier sur les versions Android cibles. Le manifeste fusionné doit être contrôlé, pas seulement le fichier de l’app.

Conserver la précision et l’horodatage réels. Ne pas inventer les champs d’activité/mouvement fournis auparavant par Transistorsoft. Les données de batterie sont optionnelles selon le contrat retenu. Le filtrage distance/fréquence sera réglé après mesure, avec un plafond d’envoi pour maîtriser réseau et batterie.

Le suivi hors réseau complet dépend d’une réception serveur idempotente et sensible à l’heure de mesure. Sans cette évolution, une V1 pourrait n’envoyer que la position récente au retour réseau, mais ne devrait pas être présentée comme conservant tout le trajet. Cette dégradation n’est pas choisie par défaut.

## Sources primaires consultées

- [Fused Location Provider](https://developers.google.com/location-context/fused-location-provider).
- [Android : permissions et service de premier plan](https://developer.android.com/develop/sensors-and-location/location/permissions).
- [Android : type de service location](https://developer.android.com/develop/background-work/services/fgs/service-types#location).
- [Android : arrêt d’un service par l’utilisateur](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping).
- [Expo Location et limites de terminaison](https://docs.expo.dev/versions/latest/sdk/location/).
- [Installation de modules Expo dans React Native](https://docs.expo.dev/bare/installing-expo-modules/) et [licence Expo](https://github.com/expo/expo/blob/main/LICENSE).
- [Geolocation communautaire](https://github.com/michalchudziak/react-native-geolocation).
- [Mauron85 : documentation et licence](https://github.com/mauron85/react-native-background-geolocation).

Les versions exactes des nouvelles dépendances seront sélectionnées et figées après le prototype de compatibilité. Les cartes, itinéraires et leurs éventuels frais ne sont pas modifiés par le remplacement du moteur GPS.
