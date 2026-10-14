# Cosmos Discipline Shield — Android Edition

**PWA installable** pour la discipline numérique sur Android. Zéro code natif, zéro ADB — tout passe par les permissions système accessibles en un clic.

## Fonctionnalités

| Onglet | Description |
|--------|-------------|
| **Aujourd'hui** | Temps d'écran auto (Usage Stats), habitudes avec streaks, compteurs d'abstinence (jours/heures/minutes temps réel), limite d'écran ajustable |
| **Grilles Mois** | *Verte* : taux de réussite habitudes par jour (gradient vert) • *Rouge* : rechutes & dépassements écran (gradient rouge) |
| **Bouclier Apps** | Sélection d'apps → à l'ouverture : **Grayscale auto** + **Breath Gate** (6s respiration guidée) → choix durée 1–5 min → re-pop automatique |
| **Réglages** | Liens directs vers permissions Android : Données d'utilisation, Affichage par-dessus, Notifications (couvre-feu 21h30 + bilan matin) |

## Flux principal

1. **Ouvre l'app** → active des presets (Lecture, Sport, Sommeil, No Porn, Limite écran) ou crée les tiens
2. **Ajoute tes apps pièges** (Insta, TikTok, YouTube…) dans *Bouclier Apps*
3. **Lance une app** depuis le bouclier → écran passe en N&B + 6s respiration → choisis 1–5 min → session démarre
4. **Temps écoulé** → re-pop Breath Gate → tu décides de continuer ou fermer
5. **Chaque matin** : bilan sommeil + validation veille → streaks & grilles mis à jour

## Installation

1. Ouvre `index.html` sur ton téléphone (Chrome / Firefox / Edge)
2. Menu ⋮ → **Installer l'application** / **Ajouter à l'écran d'accueil**
3. Lance l'icône → autorise les 3 permissions via les boutons *Réglages*
4. C'est prêt — fonctionne 100% hors-ligne, données en `localStorage`

## Stack

- **HTML/CSS/JS** vanilla (un seul fichier)
- **Tailwind CSS** via CDN
- **Lucide Icons** via CDN
- **PWA** : manifest dynamique, `standalone`, `viewport-fit=cover`
- **Android Intents** : `USAGE_ACCESS_SETTINGS`, `MANAGE_OVERLAY_PERMISSION`

## Confidentialité

- **Aucun serveur** — tout stocké localement (`localStorage`)
- **Aucun tracking** — pas d'analytics, pas de compte
- **Permissions** : seulement ce que tu acceptes dans les réglages système

---

*Conçu pour être brutal, minimal, et efficace. Zéro friction, zéro excuse.*