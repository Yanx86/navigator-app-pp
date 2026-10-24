# Validation après fusion GPS-001.1

La PR #1 a été fusionnée dans `Yanx86/navigator-app-pp:main` le 24 octobre 2026.
Commit de fusion : `ca498ea8b7e5e55e66072efec8137ebcc66b0577`.

## État vérifié

- Dépôt local sur ce même commit, sans changement non enregistré au début de la vérification.
- Job `install_and_test` de la PR : réussi sur GitHub Actions.
- Job `android_build` : encore en cours lors de cette vérification, donc compilation non déclarée réussie.
- `Dependency Review` : échec de configuration. Le journal indique que l’analyse n’est pas prise en charge sur le dépôt et demande de vérifier l’activation de Dependency graph. Ce n’est pas un résultat d’analyse des vulnérabilités.

## Complément CI

La CI exécute désormais explicitement `:app:testDebugUnitTest` avant de construire l’APK. Elle conserve les rapports JUnit/XML et HTML pendant 30 jours, même si un test échoue. Les tests Kotlin de génération de session ne sont donc plus seulement présents dans le dépôt : leur exécution est demandée à chaque build Android de ce workflow.

Le contrôle de dépendances reste actif. Vérifier sa configuration dans les paramètres du fork :
https://github.com/Yanx86/navigator-app-pp/settings/security_analysis

La réussite des tests de session ne prouve pas la tenue du GPS écran éteint ni le comportement réseau sur téléphone. La recette physique et la compilation release restent nécessaires. Le suivi hors réseau durable et la nouvelle authentification restent des lots distincts.

Références :
- PR fusionnée : https://github.com/Yanx86/navigator-app-pp/pull/1
- CI de la PR : https://github.com/Yanx86/navigator-app-pp/actions/runs/40123748523
- Contrôle de dépendances : https://github.com/Yanx86/navigator-app-pp/actions/runs/40123748545
