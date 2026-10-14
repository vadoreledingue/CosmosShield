# Cosmos Shield — application Android

Application Android (Kotlin) qui embarque ton interface HTML et ajoute les vraies fonctions système :
temps d'écran réel, interception des apps (sas de respiration), noir & blanc, notifications 21h30 / 8h.
Aucune dépendance externe, aucun accès Internet : tout fonctionne hors ligne.

## Obtenir l'APK

**Option A — Android Studio** (le plus simple)
1. Installe Android Studio, puis `File > Open` et choisis le dossier `CosmosShield`.
2. Attends la synchronisation Gradle (JDK 17 inclus dans Android Studio).
3. Branche ton téléphone (débogage USB) et clique ▶ **Run**, ou `Build > Build APK(s)`.

**Option B — sans rien installer (GitHub)**
1. Crée un dépôt GitHub et envoie-y ce dossier.
2. Onglet **Actions > Build APK** : télécharge l'artefact `CosmosShield-apk` (fichier `app-release.apk`).
3. Copie l'APK sur le téléphone et installe-le (autorise « sources inconnues »).

**Option C — ligne de commande** : `./gradlew assembleRelease` → `app/build/outputs/apk/release/`.

## Premier lancement : permissions (onglet Réglages)
1. **Données d'utilisation** — temps d'écran réel + détection de l'app ouverte
2. **Affichage par-dessus les autres apps** — indispensable pour que le sas s'ouvre devant Instagram, etc.
3. **Notifications** — couvre-feu 21h30 et bilan du matin 8h
4. **Batterie sans limite** — évite que le constructeur (Xiaomi, Samsung, Huawei…) coupe le bouclier

Ensuite, onglet **Bouclier Apps** > « Choisir une application installée ».

## Noir & blanc réel (optionnel, une seule fois)
Android l'interdit aux apps sans autorisation spéciale. Depuis un PC, téléphone en débogage USB :

    adb shell pm grant com.cosmos.shield android.permission.WRITE_SECURE_SETTINGS

Sans cela, le sas de respiration fonctionne quand même, mais l'écran reste en couleur.
Bouton de secours dans Réglages : « Rétablir les couleurs ».

## Modifier l'interface
L'UI est dans `web/index.html`. Après modification :

    cd web && npm install && npm run build

(régénère `app/src/main/assets/www`, avec Tailwind, les icônes et les polices embarqués).

## Structure
- `web/` : source de l'interface (HTML + build Tailwind hors ligne)
- `app/src/main/assets/www/` : interface compilée, servie dans la WebView
- `NativeBridge.kt` : pont JavaScript ↔ Android
- `ShieldService.kt` : surveillance de l'app au premier plan (service au premier plan)
- `Usage.kt` : temps d'écran · `Grayscale.kt` : noir & blanc · `Reminders.kt` : alarmes et notifications
