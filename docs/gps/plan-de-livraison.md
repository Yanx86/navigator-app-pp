# GPS-001 — Plan de livraison et critères de recette

Statut : architecture acceptée ; code du lot GPS-001.1 réalisé, critères de sortie Android/terrain non encore satisfaits. Voir le rapport de livraison.

## Lots et livrables

| Lot | Contenu | Livrable et condition de sortie |
| --- | --- | --- |
| GPS-001.0 | Inventaire, comparaison, décisions de périmètre | Présent dossier ; ADR accepté ou révisé |
| GPS-001.1 | Établir le build de référence puis prototype Android du service, permissions, positions et notification | Rapport de compatibilité RN 0.86/API 36 ; APK de test ; essai écran éteint sur appareil physique avant migration complète |
| GPS-001.2 | Interface indépendante du fournisseur, état natif, stockage durable, transport et session | Code testé ; contrat de données et politique de file documentés |
| GPS-001.3 | Contrat FleetOps pour points horodatés et identifiants stables | Tests de rejeu, isolation chauffeur/organisation et points désordonnés ; position courante monotone selon heure de mesure |
| GPS-001.4 | Basculer contexte et positions ponctuelles ; retirer dépendances, licence et réglages Transistorsoft Android/CI | Build release sans clé Transistorsoft ; pas de double moteur actif ; documentation de configuration mise à jour |
| GPS-001.5 | Recette terrain, profil batterie et réseau | Rapport avec appareils, versions Android, commits, mesures et limites ; procédure de livraison et de retour arrière |

Le lot serveur doit précéder l’activation du replay complet. Un identifiant de point unique doit être dédupliqué dans une opération atomique et dans le périmètre du chauffeur/organisation. Vérifier les effets secondaires : historique, véhicule, carte, géofencing et notifications ne doivent pas rejouer une ancienne livraison comme un événement présent.

FleetOps est un sous-module qui pointe actuellement vers l’amont Fleetbase. Pour modifier le serveur, il faudra un fork maîtrisé de FleetOps, une branche/PR dans celui-ci, puis une PR du dépôt parent mettant à jour l’URL si nécessaire et la référence du sous-module. Un commit local détaché dans le sous-module ne suffit pas à rendre la livraison reproductible.

## Traçabilité du travail

- Une branche et une PR par lot cohérent ; commits lisibles associés à GPS-001.x.
- Dans chaque PR : problème, comportement livré, fichiers/contrats touchés, tests réellement exécutés, limites et procédure de retour arrière.
- Les résultats « non exécuté », « échoué » et « réussi » sont distincts. Une compilation debug ne valide pas une release ni le comportement en veille.
- Conserver les locks de dépendances et noter les versions Android/SDK utilisées. Séparer un éventuel correctif du build initial de la migration GPS.
- Ajouter les références de commits/PR, l’empreinte de l’APK et la configuration non secrète au rapport de livraison.
- Ne pas versionner secrets, keystore, mots de passe, jetons ou traces GPS réelles identifiantes.
- iOS est hors périmètre fonctionnel. Documenter les fichiers iOS hérités et les workflows à désactiver/adapter ; ne pas annoncer une compatibilité iOS après retrait des dépendances partagées.

## Tests de comportement

| Cas | Résultat attendu |
| --- | --- |
| Chauffeur non connecté ou hors service | Aucune nouvelle acquisition continue ni nouvel envoi de cette session |
| Mise en ligne et permission accordée | Un seul service et une seule notification ; état réel remonté à l’interface |
| Permission refusée, approximative ou retirée | Parcours explicite, pas de crash ni de suivi prétendument précis ; arrêt si permission requise absente |
| Écran éteint, app en fond | Points reçus selon profil configuré ; interruptions et latences mesurées |
| Réseau absent puis rétabli | File persistante bornée ; reprise selon contrat serveur sans doublons ni recul de position |
| Processus recréé | État/session vérifiés avant toute collecte ou reprise ; aucune fuite de points entre sessions |
| Jeton expiré ou révoqué | Suspension et demande de reconnexion ; aucun retry infini |
| Changement chauffeur ou instance | Aucune position de A envoyée avec la session de B |
| Reboot / arrêt forcé | Pas de promesse de reprise autonome ; réouverture et mise en ligne rétablissent le service |
| Retrait de l’app des récents | Comportement observé et documenté pour chaque fabricant testé |
| Coordonnées invalides, précision médiocre, timestamps désordonnés | Validation et politique documentées ; pas de point artificiel ni de trajet corrompu |
| Release sans clé commerciale | Installation et fonctionnement effectifs ; dépendances natives inspectées |

Tests automatiques : transitions de session, sérialisation, file/retry/déduplication, permissions, contrat HTTP et isolation serveur. Tests natifs/instrumentés : démarrage/arrêt du service, persistance après recréation, notification. Les mocks Jest seuls ne prouvent pas le suivi en arrière-plan.

## Protocole terrain proposé

Utiliser au moins un appareil Android proche du système standard et un appareil du constructeur réellement utilisé par les chauffeurs. Tester une version récente et la plus ancienne officiellement retenue pour le parc, sans déduire le parc du seul minSdk 24.

Scénario proposé : trajet de 60 minutes dont au moins 30 écran éteint, arrêt stationnaire, coupure réseau de 10 minutes et retour réseau, fin de service puis reconnexion. Ajouter reboot et arrêt forcé comme scénarios séparés.

Avant recette, figer des seuils métier : fréquence/distance, âge maximal acceptable de position, taille/durée maximale de file et politique de débordement. Mesurer latence p95, trous de collecte, points perdus, consommation réseau et baisse batterie dans des conditions répétables. Aucun chiffre de consommation ni autonomie n’est promis avant mesure.

## Livraison et retour arrière

Déployer d’abord sur un appareil de test et un environnement Fleetbase de développement. Archiver l’APK de référence, le schéma de stockage et les versions serveur. Une évolution serveur doit être additive au début afin d’accepter temporairement l’ancien client.

En cas d’échec : arrêter la nouvelle collecte, préserver ou purger explicitement la file selon la politique de session et revenir à une version validée. L’ancien SDK ne constitue pas un retour arrière exploitable en production sans droit de licence ; à défaut, désactiver le suivi et informer le chauffeur plutôt que prétendre qu’il fonctionne.

## Chantier distinct AUTH-001

À cadrer ensuite : identification chauffeur, mécanisme de connexion sans OTP fixe, durée de session, stockage protégé, renouvellement/révocation et autorisation serveur. GPS-001 consomme une session valide et traite son expiration ; il ne crée aucun contournement d’authentification. Une mise en production reste dépendante de la résolution d’AUTH-001.
