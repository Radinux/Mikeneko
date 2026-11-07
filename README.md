<div align="center">

<img src="docs/logo.svg" width="140" alt="Logo de Mikeneko : une tête de chat tricolore">

# Mikeneko 三毛猫

ฅ^•ﻌ•^ฅ **Le client YouTube libre pour Android, tout en verre liquide** ✧･ﾟ<br>
Sans pubs · sans compte Google · avec des oreilles de chat (=^･ω･^=)

[![Dernière version](https://img.shields.io/github/v/release/Radinux/Mikeneko-releases?style=for-the-badge&color=E07A2E&label=version)](https://github.com/Radinux/Mikeneko-releases/releases/latest)
![Android 8+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
[![Licence GPL-3.0](https://img.shields.io/badge/licence-GPL--3.0-55504D?style=for-the-badge)](LICENSE.txt)

### [⛩️ Télécharger Mikeneko ⛩️](https://github.com/Radinux/Mikeneko-releases/releases/latest)

</div>

---

## 🌸 Ce qu'elle sait faire (ﾉ◕ヮ◕)ﾉ✧

- 🗡️ **Sans pubs**, avec **SponsorBlock** qui saute sponsors et intros tout seul (le « coup de patte »)
  et les **dislikes** de Return YouTube Dislike.
- 🏯 **Accueil « Pour toi »** calculé sur le téléphone (historique, suggestions, abonnements) et
  **bloqueur de shorts**.
- 📜 **Abonnements sans compte** : import depuis NewPipe, FreeTube ou Google Takeout, filtre par
  chaîne, « Pas vues ».
- 🎧 **Un vrai lecteur de musique** : YouTube Music (morceaux, albums, artistes, playlists), Mix auto,
  « Pour toi » et « Tes mix », paroles synchronisées, spectre qui danse, playlists perso.
- 🍙 **Téléchargements yt-dlp** : vidéo, MP3, FLAC, Opus, M4A, sponsors coupés, écoute hors ligne.
- 🐾 **Mini-lecteur PiP** à oreilles de chat dans l'appli, et vrai PiP système en la quittant.
- 🔮 **Liquid glass** : dock, boutons et fenêtre musique en verre qui réfracte l'écran (Android 13+).
- 💮 Commentaires, chapitres, pages de chaîne et de playlist, **Bibliothèque** (historique, à
  regarder plus tard, J'aime).
- 🌙 **« T'as vu l'heure ? »** après une heure de visionnage (－_－) zzZ
- 🍥 **Mises à jour automatiques** depuis GitHub, en 🇫🇷 français et 🇬🇧 anglais.

## 🎴 Installer (｀・ω・´)ゞ

1. Télécharge le dernier APK sur la
   [page des versions](https://github.com/Radinux/Mikeneko-releases/releases/latest)
   (`arm64-v8a`, pour tous les téléphones récents).
2. Ouvre-le sur le téléphone et autorise l'installation.
3. C'est tout : l'appli se met ensuite à jour d'elle-même ٩(◕‿◕)۶

## 🥷 Reprendre le développement

**Version : 2.6.2** (publiée en release GitHub ; dernier APK partagé à la main : `Mikeneko-2.1.2.apk`).
La version est automatique : `app/version.properties` garde la version de la prochaine compilation
release ; chaque `assembleRelease` / `installRelease` la prend puis prépare la suivante (dernier
chiffre +1, `versionCode` +1). Nouvelle fonction : `minor` +1 et `patch=0` à la main. Pas de x.20 : là où on arriverait à x.20.0, c'est une version majeure ((x+1).0.0).

📜 Les rouleaux du projet : `NOTES-TECHNIQUES.md` (comment et pourquoi, pièges, procédures)
et `FIXES.md` (à faire / à vérifier).

## 🎌 Nouveautés ⸜(｡˃ ᵕ ˂ )⸝♡

### 2.6.0 → 2.6.2

- **Musique « Pour toi ».** Sur l'accueil Musique, d'après ce que tu écoutes :
  - **Pour toi** : une étagère de morceaux à découvrir, tirés des mix YouTube Music de tes 3 artistes
    les plus écoutés récemment, sans ce que tu as déjà écouté ou mis en playlist ;
  - **Tes mix** : des playlists thématiques fabriquées pour toi, avec une pochette en mosaïque :
    un « Mix *artiste* » par artiste favori (lui et des artistes proches), et **Redécouvertes** (des
    morceaux de tes playlists pas écoutés depuis un moment). Un mix s'ouvre en page (lecture,
    aléatoire) ; **Garder** en fait une vraie playlist.
  - 3 demandes à YouTube au plus, gardées 6 h ; recalculé quand tes artistes changent.
- **Téléchargements réparés** : « yt-dlp n'a pas pu être installé » (message vide) dans la version
  release. La réduction R8 cassait la bibliothèque qui décompresse Python et ffmpeg ; elle est
  maintenant protégée. L'erreur affiche aussi sa vraie cause, et un nouvel essai est possible sans
  relancer l'appli.
- 2.6.1 : un téléchargement qui échouait avec une trace Python (« Traceback… ») : l'appli refait
  alors proprement son installation de Python et de yt-dlp, puis réessaie une fois. L'erreur affichée
  est la vraie raison (la fin de la trace), et la trace complète va dans le journal.
- 2.6.2 : le **spectre** de la fenêtre musique devient une **vague continue** qui monte du bas de la
  fenêtre, en fond derrière les boutons (rousse sur la partie jouée, pâle sur la suite). Les barres
  au-dessus de la progression poussaient les boutons trop bas (coupés en paysage). Et en paysage avec
  la barre d'état affichée, le **dock** descend sous sa bande noire (son haut était rogné).

### 2.5.0 → 2.5.3

- **Spectre de la musique.** Au-dessus de la barre de progression de la fenêtre musique, 40 barres
  arrondies dansent avec les fréquences du morceau (graves à gauche, aigus à droite) : rousses sur
  la partie déjà jouée, pâles sur la suite ; elles retombent en pause. Le son est analysé dans le
  lecteur même (pas besoin du micro), et seulement quand la fenêtre est ouverte.
- 2.5.1 : la bande noire de la barre d'état a des coins inversés plus arrondis (34 dp).
- 2.5.2 : le spectre est **fluide** (il saccadait : le son arrive par paquets, environ 4 par seconde ;
  il est maintenant analysé à chaque image de l'écran).
- 2.5.3 : dans la **pilule musique**, la pochette reste en place (elle change en fondu) et seul le
  titre suit le doigt : dès qu'on commence à glisser, le **titre du morceau suivant** (ou précédent)
  arrive à côté, puis se pose. La fenêtre musique s'ouvre et se ferme avec **un seul petit
  rebond** (elle rebondissait en trop).

### 2.4.0

Ta liste « Mikeneko fixes list » :
- **Bloqueur de shorts réparé.** Des shorts passaient encore : le « Pour toi » se nourrit de
  l'historique, qui oubliait qu'une vidéo était un short, et le flux des abonnements ne le disait
  pas. Maintenant : le lien `/shorts/` du flux RSS, l'info gardée dans l'historique et les listes, et
  toute vidéo d'une minute au plus comptent comme des shorts.
- **Playlists musique** : le nombre de morceaux et la **durée totale** sous le titre (« 24 morceaux ·
  1 h 32 min »), aussi pour les albums et playlists YouTube Music ; plus de numéros devant les
  morceaux (coupés après 99).
- **Pochettes de playlists** : « Pochette » choisit une photo du téléphone ; sans photo, une
  **mosaïque** des pochettes des 4 premiers morceaux (partout : accueil Musique, Ta musique, page de
  la playlist, playlists vidéo de la Bibliothèque). « Mosaïque » revient à la mosaïque.
- **Paroles** : on ferme le panneau en le **tirant vers le bas** (par la barre du haut, ou en
  continuant de tirer quand les paroles sont en haut) ; il suit le doigt et revient s'il est lâché tôt.
  Et tant que les paroles sont ouvertes, **l'écran ne s'éteint pas**.
- **Barre d'état affichée** (réglage « masquée » coupé) : une bande noire derrière elle, avec des
  coins arrondis inversés, pour que rien ne passe sous l'heure et les icônes.
- **Lecteur musique plus transparent** : le voile de la fenêtre et l'assombrissement derrière sont plus légers.

<details>
<summary><b>🏮 Les archives du clan : versions 2.3.0 → 1.1.0</b></summary>

### 2.3.0

- **Tes shorts.** Un signet dans le lecteur Shorts (au-dessus de « partager ») garde le short dans
  **Bibliothèque › Tes shorts**, une liste à part des vidéos : une grille de miniatures en hauteur ;
  un tap ouvre le lecteur Shorts et on glisse dans tes shorts gardés ; ✕ en retire un. La liste est
  aussi dans la sauvegarde Mikeneko (.xml, section `<shorts>`).
- **Réglage « Pas de shorts »** (Réglages › Lecture) : plus aucun short nulle part : accueil et
  « Pour toi », recherche, abonnements, suggestions du lecteur, onglet Shorts des chaînes et tuile
  « Tes shorts ».

### 2.2.0 → 2.2.1

- **Lecteur Shorts.** Un short (onglet Shorts d'une chaîne, recherche, accueil, abonnements, lien
  `/shorts/`) s'ouvre en **plein écran, en hauteur**, et tourne en boucle. Glisser vers le haut / le
  bas : short suivant / précédent, d'abord ceux de la liste d'où on l'a ouvert, puis les shorts
  suggérés par YouTube. Un tap met en pause. Par-dessus, en verre : la chaîne (vers sa page) et le
  titre en bas, et à droite J'aime (pouce plein quand aimé), commentaires et partager ; un fin trait
  roux montre la progression. Retour (ou ⌄) : la lecture s'arrête et on revient d'où on venait. Pas
  de plein écran en tournant le téléphone, et un short ne prend pas de place dans la reprise des vidéos.
- 2.2.1 : un short ouvert depuis les abonnements ou « Pour toi » s'ouvrait encore dans le lecteur
  normal (ces listes ne savent pas que c'est un short) : il est maintenant reconnu une fois chargé
  (YouTube le dit, ou vidéo en hauteur de 3 min au plus) et passe dans le lecteur Shorts.

### 2.1.8 → 2.1.12

- Le pouce « J'aime » du lecteur devient **plein** quand la vidéo est aimée (en plus de la capsule
  rousse) : on voit tout de suite que le clic a marché.
- 2.1.10 (la 2.1.9, compilée, n'a pas été publiée) : la pilule de mise à jour n'est plus coupée (« Mikeneko 2.… ») : deux lignes, « Mise à jour
  prête » au-dessus de « Mikeneko X », et un bouton court « Installer ».
- 2.1.10 : dans la recherche vidéo, les capsules de tri (Filtres, Meilleurs, Récentes…) **montent et
  s'effacent** avec le haut des résultats, comme les rubriques de l'accueil (elles restent si les
  filtres sont ouverts) ; glisser verticalement dessus fait défiler les résultats.
- 2.1.11 : version de test, sans changement, publiée une fois la 2.1.10 installée pour voir la
  nouvelle pilule de mise à jour (une pilule vient toujours de la version installée).
- 2.1.12 : la pilule de mise à jour **s'en va toute seule après 6 secondes** (elle cachait le haut de
  l'écran) ; la mise à jour reste dans Réglages › À propos et s'installe quand on quitte l'appli.

### 2.1.3 → 2.1.7

- **Grand nettoyage du code, sans rien changer à l'écran.**
  - La connexion Google, éteinte depuis longtemps, est retirée avec les **Play Services** : l'APK
    ne dépend plus d'aucun service Google. Les commentaires restent lisibles (lecture seule).
    L'ancien code reste dans l'historique git (`data/GoogleAccount.kt` au commit `066b0c2`).
  - Les gros fichiers sont découpés : le lecteur (`PlayerScreen`, `FullscreenPlayer`,
    `PlayerControls`), la fenêtre musique (`MusicWindow`, `MusicDock`, `MusicPlayerContent`,
    `MusicQueue`, `MusicPlayerState`), les pages musique (`MusicPages`, `MusicHome`, `MusicSearch`,
    `MusicPageHeader`) ; `VideoFrames` et `PlayerTypes` sortent de `PlayerHolder`.
  - Les anciens noms « Radio » de l'onglet Musique deviennent « Music » (`MusicScreen.kt`,
    `MusicState`, `MusicPage`, `Route.Musique`). Le bouton « Radio » d'une vidéo garde son nom.
  - Code, textes et icônes inutilisés retirés ; doublons fusionnés (format du temps `formatClock`,
    partage `shareLink`, `lerp`) ; imports rangés ; indentation recalée.
- 2.1.4 : en portrait, les boutons du centre de la vidéo (précédent / lecture / suivant) se
  centrent au-dessus de la barre du bas : « suivant » collait le bouton des réglages. Beaucoup trop
  haut ; 2.1.6 : ils restent au milieu et remontent seulement de ce qu'il faut (2.1.5 non installée).
- 2.1.7 : la même que la 2.1.6, publiée en release GitHub pour arriver par la mise à jour automatique.

### 2.1.0 → 2.1.2

- **Mises à jour automatiques depuis GitHub.** Les APK sont publiés en releases sur le dépôt public
  [Radinux/Mikeneko-releases](https://github.com/Radinux/Mikeneko-releases) (le code reste privé).
  Au lancement (une fois par jour au plus), Mikeneko regarde la dernière release ; si elle est plus
  récente, l'APK se télécharge en fond et **s'installe tout seul, sans appui** (Android 12+), quand on
  quitte l'appli et qu'aucune musique ne joue. Il faut seulement autoriser une fois Mikeneko à
  installer des applis (Réglages › À propos › Mises à jour › Autoriser). Réglages › À propos montre
  l'état, « Vérifier », « Installer maintenant » et « Nouveautés ».
- 2.1.1 : version de test, sans changement, publiée pour essayer la mise à jour automatique.
- 2.1.2 : une mise à jour téléchargée affiche une **pilule en verre en haut de l'écran**, « Mikeneko X
  est prête » avec **Mettre à jour** (tout de suite) et ✕ (plus tard : elle s'installe en quittant
  l'appli). Réglages › À propos : les boutons ne s'écrasent plus. Publiée seulement en release GitHub
  (arrivée sur le téléphone par la mise à jour automatique).

### 2.0.0 → 2.0.4

- **Une vraie page lecteur en paysage** : la vidéo et ses commandes à gauche, tout le reste à droite
  dans une colonne qui défile (barre de temps, SponsorBlock, titre, chaîne, j'aime / plus tard /
  playlist / télécharger…, description, commentaires, chapitres, suggestions).
- **Sortir du plein écran en tenant le téléphone en paysage ne force plus le portrait** : on arrive
  sur cette page, toujours en paysage. Le portrait n'est forcé que si on était entré en plein écran
  avec le bouton en tenant le téléphone droit.
- Ouvrir une vidéo en paysage (depuis l'accueil en colonnes, le mini-lecteur…) ouvre cette page ;
  tourner le téléphone depuis le portrait passe toujours en plein écran, et la page paysage revient
  au portrait avec la rotation habituelle d'Android.
- 2.0.1 : **la barre de progression est dans la vidéo** (portrait et paysage, comme YouTube) : avec
  les commandes, le temps en bas à gauche, les boutons à droite et la barre complète dessous ; sans
  elles, une fine ligne rousse au ras du bas de la vidéo. Les commandes ne se cachent pas pendant un
  glissé sur la barre ni pendant un marquage SponsorBlock.
- 2.0.2 : en paysage, plus de ligne « Now playing » au-dessus de la vidéo (trop de place) : la flèche
  pour réduire est un bouton en verre en haut à gauche de la vidéo, avec les commandes ; la vidéo
  prend 60 % de la largeur.
- 2.0.3 : en paysage, la vidéo est centrée sur toute la hauteur de l'écran (elle était décalée vers le
  bas de la hauteur de la barre d'état), et glisser la vidéo vers le bas la réduit en mini-lecteur.
- 2.0.4 : en paysage, entrer en plein écran ou en sortir (bouton ou retour) fait un **zoom** : la vidéo
  grandit depuis sa place dans la page jusqu'à remplir l'écran, et y revient en rétrécissant (la page
  apparaît derrière en fondu). Avant, ça changeait d'un coup.

### 1.19.0 → 1.19.8

- **Plein écran en tournant le téléphone : seule la vidéo tourne**, plus toute l'interface. Sur
  l'écran du lecteur, Android n'anime plus la rotation ; la vidéo part de sa place en haut du
  portrait, couchée, et pivote en grandissant jusqu'à remplir l'écran. Au retour en portrait, elle
  pivote du plein écran jusqu'à sa place, et la page apparaît derrière en fondu. Le bouton plein écran
  attend aussi que l'écran ait tourné avant de basculer.
- 1.19.1 → 1.19.2 : **le mini-lecteur en tête de chat calico** (d'après le dessin de l'utilisateur,
  aux couleurs du logo) : un cadre blanc autour de la vidéo, dont les côtés montent en grandes
  oreilles arrondies ; oreille et tache gris foncé à gauche (intérieur plus foncé), oreille et tache
  rousses à droite (intérieur clair), contour sombre. Les oreilles poussent en « boing » quand il atterrit.
- 1.19.3 → 1.19.4 : **plus de violet** : les pochettes absentes de la musique (tuiles « Your library »,
  « Downloaded », « New playlist », pilule du lecteur…), la lueur des pages sans pochette, les avatars
  sans photo des commentaires et les miniatures en attente du menu ⋮ passent au roux sombre
  (`RouxNuit`, icônes et initiales `RouxClair`), dans le thème calico.
- 1.19.5 : **une vidéo n'apparaît plus dans la musique.** Lancer une vidéo puis aller dans Musique :
  la pilule montrait la vidéo comme un morceau (et son ▶ / ⏸ contrôlait la vidéo). Elle montre
  maintenant le morceau que la vidéo a mis de côté (▶ le reprend, la vidéo s'arrête), ou « Pick some
  music » ; ouvrir la fenêtre remet ce morceau, en pause.
- 1.19.6 : **le mini-lecteur vidéo n'est plus dans Musique.** En allant dans Musique, la vidéo se met
  en pause et son mini-lecteur se cache ; en revenant sur Home, Subscriptions ou Library, il
  réapparaît, en pause au même endroit. Si un morceau l'a remplacée entre-temps, elle revient
  (rechargée, en pause) dès que la musique ne joue plus.
- 1.19.7 → 1.19.8 : mini-lecteur : les boutons se cachent seuls au bout de 3 s (chaque appui relance
  le minuteur), et la barre de temps n'apparaît plus qu'avec eux ; tout arrive et repart en fondu.

### 1.18.0 → 1.18.1

- **L'import des abonnements est dans Réglages › Sauvegarde** (« Importer des abonnements » :
  NewPipe, FreeTube, Google Takeout). La grande carte d'import ne reste plus en haut de l'onglet
  Abonnements ; quand il n'y a encore aucune chaîne, un petit mot et un bouton « Importer » mènent aux
  réglages.
- 1.18.1 : **les images des chaînes suivies** reviennent. L'import Takeout n'en a pas : elles arrivent
  en regardant une vidéo ou en ouvrant la page d'une chaîne (sans requête de plus), et doucement en
  fond pendant que l'onglet Abonnements est ouvert (une toutes les 4 s, 30 au plus par lancement).

### 1.17.0 → 1.17.2

- **Barre de progression sur les miniatures** : une barre orange sous la miniature des vidéos
  commencées (les 10 dernières, voir 1.16.0), partout où il y a des miniatures (accueil, recherche,
  chaînes, abonnements, historique, suggestions).
- **Suggestions dans la page du lecteur** : sous les commentaires, la liste « Up next », en plus du
  tiroir en verre du bas. Toucher une suggestion la lance et la page repart du haut.
- 1.17.1 : le tiroir en verre du bas n'apparaît plus que pour une vidéo d'une playlist (onglets
  Playlist / Up next) ; une vidéo seule a ses suggestions dans la page.
- 1.17.2 : plein écran : le bouton « Up next » est centré sur l'écran, sous les commandes (il était
  centré dans la place entre le temps et le bouton de sortie, et décalé par l'encoche).

### 1.16.0

- **Reprise des vidéos** : l'appli retient où tu en étais dans les 10 dernières vidéos, même après
  l'avoir fermée. En rouvrant l'une d'elles, la lecture reprend là. Une vidéo finie (moins de 20 s
  de la fin) ou à peine commencée (moins de 10 s) repart du début. Pas pour la musique ni les directs.
  « Effacer l'historique » efface aussi ces positions, et rien n'est gardé quand l'historique est en pause.

### 1.15.4 → 1.15.5

- **Hors ligne = seulement ce qui est téléchargé.** Sans réseau, seules les playlists téléchargées
  (et les fichiers yt-dlp) se lisent. Le cache caché des morceaux écoutés en entier sert encore quand
  il y a du réseau (démarrage plus rapide, moins de requêtes à YouTube), mais plus hors ligne. Retirer
  le téléchargement d'une playlist la rend donc tout de suite illisible sans réseau. (1.15.4 effaçait
  les morceaux du cache au retrait ; 1.15.5 remplace ça.) Le morceau en cours et ses voisins restent
  en mémoire du lecteur et peuvent finir de jouer.

### 1.15.3

- Pages album, playlist, artiste et téléchargés : **un seul bouton lecture**. Celui de l'en-tête monte
  avec la page, puis glisse et rétrécit jusqu'à sa place dans la pilule du haut, où il reste. Avant,
  un deuxième petit bouton apparaissait en fondu dans la pilule.
- Le bouton **Shuffle** de ces pages est un interrupteur : orange quand la liste joue en aléatoire ;
  un nouvel appui coupe l'aléatoire (l'ordre revient, le morceau continue). Si une autre liste joue,
  l'appui lance celle-ci en aléatoire, comme avant.

### 1.15.2

- Musique : en tirant la fenêtre du lecteur vers le bas, la vitre pouvait rester figée à mi-course
  (vers un quart de l'ouverture). Elle se pose maintenant toujours, même si le geste est annulé.

### 1.15.1

- Accueil : glisser vers le haut ou le bas en commençant sur une capsule de rubrique fait défiler
  la liste (et, tout en haut, tirer vers le bas rafraîchit). Avant, la capsule en verre prenait le geste.

### 1.14.7 → 1.15.0

- 1.14.7 : **l'import Google Takeout marche en français** (`SubscriptionImport`) : l'en-tête du
  `abonnements.csv` est traduit selon la langue du compte (« ID des chaînes,URL des chaînes,Titres des
  chaînes ») et commence par un BOM ; il n'est plus lu. Toute ligne avec un identifiant `UC…` est une
  chaîne, les titres entre guillemets (avec virgules) sont bien lus.
- 1.15.0 : **fichier de sauvegarde Mikeneko en .xml** (`data/Backup.kt`, `MikenekoBackup`) : Réglages →
  Sauvegarde → « Exporter dans un fichier » / « Importer un fichier ». Il contient les abonnements, les
  playlists musique et vidéo, les albums et artistes sauvegardés, les radios favorites, « À regarder
  plus tard » et les vidéos aimées. L'import ajoute ce qui manque sans rien effacer (une playlist du
  même nom reçoit les morceaux qui lui manquent) et dit ce qu'il a ajouté. Le bouton « Importer » des
  Abonnements accepte aussi ce fichier (il n'en prend que les abonnements).
  Vérifié sur la VM Pixel 10 : Takeout français (BOM, virgules), export, import (1 chaîne, 2 playlists,
  2 albums/artistes, 1 vidéo ajoutés), réimport (« rien de nouveau »).

### 1.14.0 → 1.14.6

- **Plus de liquid glass** :
  - Pages musique (playlist, album, artiste, téléchargés) : la barre qui arrive en descendant est
    une **pilule en verre** (titre + bouton lecture orange), entre le retour et ⋮ ; la liste se
    déforme dessous. Elle remplace le voile sombre.
  - **Capsules en verre** (`GlassChip`, `ui/components/Components.kt`), posées sur les listes :
    rubriques de l'accueil (dessinées au-dessus de la liste dans `HomeOverlay`, elles montent et
    s'effacent avec le haut de la liste, `HomeState.categoriesShift`), tri et filtres d'une recherche,
    filtres de la bibliothèque musique et genres de la recherche musique (fixes sous la barre).
    Choisie, une capsule se teinte d'orange.
  - Bibliothèque musique : « Ta bibliothèque » est dans la pilule de la barre (`FixedPageBar`).
- 1.14.1 : la pilule en verre arrête les taps une fois visible (ils traversaient jusqu'au morceau dessous).
- 1.14.2 : historique / suggestions de recherche, commentaires, « Up next » du plein écran et tiroir de
  playlist : le verre dépoli se voit vraiment (teinte sombre allégée de ~75 % à ~35-50 %, bords plus
  bombés, flou un peu plus fort pour garder le texte lisible).
- 1.14.3 : les suggestions (« Up next ») quittent la page du lecteur pour un **tiroir en liquid glass
  dépoli** en bas (le tiroir de playlist, `PlaylistDrawer`, devenu tiroir à onglets) : fermé, une barre
  « Up next » + la prochaine vidéo ; tiré vers le haut, la liste. Quand la vidéo vient d'une playlist,
  deux onglets, « Playlist » et « Up next ». Toucher une suggestion la lance et referme le tiroir.
- 1.14.4 : ce tiroir est en liquid glass **clair, sans dépoli** (la page se voit à travers, déformée
  sur les bords) ; teinte un peu plus sombre et ombre sous les textes pour qu'ils restent lisibles.
- 1.14.5 : le panneau « Up next » du **plein écran** (`FullscreenSuggestions.kt`) en liquid glass clair,
  sans dépoli (la 1.14.2 l'avait rendu plus flou par erreur) ; ombre sous les titres.
- 1.14.6 : ce panneau est centré (la marge de l'encoche de la caméra, d'un seul côté, s'applique aux deux).

### 1.13.0 → 1.13.6

- **Télécharger une playlist perso, comme sur Spotify** (`player/PlaylistDownloads.kt`) : un rond ⤓
  dans l'en-tête des playlists musique (anneau de progression, orange une fois tout téléchargé), une
  capsule « Télécharger » sur les playlists vidéo. Les morceaux / vidéos vont dans deux réserves à part
  de la sauvegarde hors ligne (`OfflineCache.Store.SavedMusic` / `SavedVideos`, dans les données de
  l'appli, sans limite ni effacement automatique) : rien de visible dans le téléphone, tout se lit
  dans l'appli sans réseau. Un à la fois, 2,5 s de pause entre deux (YouTube n'aime pas les
  rafales) ; ce qui a déjà été lu en entier est recopié depuis la sauvegarde de lecture, sans réseau.
  Vidéos en 720p au plus. Un élément ajouté à une playlist téléchargée se télécharge, un élément
  retiré (ou la playlist plus téléchargée, après confirmation) est effacé. Un échec (pas de réseau)
  est réessayé 10 min plus tard. Le petit ⤓ orange à droite d'un morceau = hors ligne.
- Une vidéo d'une playlist téléchargée se lance depuis la réserve (sans flux YouTube), et ses
  détails (description, suggestions) arrivent ensuite si le réseau répond.
- L'appli ne se vide pas en arrière-plan pendant un téléchargement de playlist.
- Réglages → Hors ligne : la place des playlists téléchargées en plus (« Vider » ne les touche pas).
- 1.13.1 : « Aléatoire » sur les playlists vidéo, « À regarder plus tard » et « J'aime » (la liste
  mélangée devient la playlist en cours).
- 1.13.2 : pages musique (playlist, album, artiste, téléchargés) : une fois l'en-tête passé en
  descendant, le titre vient dans la barre du haut à côté du retour, et le bouton lecture reste
  accroché en haut à droite, sur un voile sombre (`PageBar` / `PageBarEffect`, comme sur Spotify).
- 1.13.3 → 1.13.5 : la barre suit vraiment le défilement (la 1.13.2 ne l'affichait jamais) ; le voile,
  le titre (qui monte) et le bouton lecture (qui grandit) arrivent progressivement, au pixel près, sur
  les 110 dp avant que le bouton lecture de l'en-tête passe sous la barre (`FADE`), sans doublon.
  1.13.6 : plus tard, sur 90 dp à partir du moment où le bas de l'en-tête arrive à 20 dp de la barre (`FADE_LEAD`).
  La tuile « Téléchargés » de l'accueil ne se coupe plus en deux (un seul mot = une ligne).

### 1.12.0 → 1.12.1

- **Sauvegarde hors ligne cachée** (`player/OfflineCache.kt`) : tout ce que le lecteur lit de YouTube
  passe par un cache Media3 (`SimpleCache`) dans le dossier cache d'Android, `hors_ligne/musique` et
  `hors_ligne/videos` (1 Go chacun, les moins récemment lus partent d'abord). Rien n'est exposé
  (ni fichiers, ni galerie). Les clés sont stables, « id:itag » (`setCustomCacheKey`), car les adresses
  des flux changent à chaque fois ; un petit index (préférences `hors_ligne_musique` / `hors_ligne_videos`)
  garde, par vidéo, les clés et le titre, la chaîne et la pochette.
- **Musique** : un morceau est chargé en entier dès qu'il joue (`player/MusicLoadControl.kt`, jusqu'à
  20 min et 40 Mo), donc il est entier dans le cache même si on le passe. Relancé ensuite (tap, file,
  morceau préparé à l'avance), il part **depuis le cache sans demander de flux à YouTube** : instantané,
  et pas de « Source error ». Les détails (artiste, album, SponsorBlock) sont chargés après, si le
  réseau répond. Sans réseau, il se lit quand même.
- **Vidéos** : une vidéo regardée en entier (vidéo et son) se relit hors ligne quand YouTube ne répond
  pas (pas de réseau ou refus).
- Réglages → « Hors ligne » : la place prise (musique, vidéos) et « Vider la sauvegarde hors ligne ».
- 1.12.1 : la 1.12.0 plantait au démarrage (`MusicLoadControl` déléguait l'interface `LoadControl` avec
  `by` : Kotlin ne transmet pas ses méthodes Java par défaut). Elle hérite maintenant de `DefaultLoadControl`.

### 1.11.0

- **Ta bibliothèque musique** (`MusicPage.Library`, dans `ui/screens/MusicPages.kt`) : une page avec des
  filtres en capsules (Tout / Playlists / Albums / Artistes / Téléchargés). Tout : nouvelle playlist,
  Téléchargés, tes playlists, puis les albums et artistes sauvegardés ; Téléchargés : les morceaux
  directement (avec Aléatoire). Le filtre choisi reste quand on revient d'un album.
- **Sauvegarder un album ou un artiste** : une capsule « Sauvegarder / Sauvegardé » dans l'en-tête de
  leur page. Le carnet est `data/MusicLibrary.kt` (le plus récent en premier, gardé sur le téléphone).
- Accueil Musique : une tuile « Ta bibliothèque » en premier dans l'accès rapide, et la rangée « Tes
  playlists » devient « Ta bibliothèque › » (son titre ouvre la page) avec aussi les albums et
  artistes sauvegardés (artistes en rond).

### 1.10.0 → 1.10.3

- **Accueil Musique en rangées** : un bonjour selon l'heure ; une grille d'accès rapide (Téléchargés,
  tes playlists, tes derniers morceaux) ; puis des rangées qui défilent de côté : « Récemment
  écoutés » (nouvel historique `data/MusicHistory.kt`, rempli à chaque morceau ou direct joué en
  audio), « Tes playlists », « Tes favoris », « Ambiances » en cartes colorées, « En direct · ambiance ».
  Un morceau récent relance son mix, un direct se relance tel quel.
- 1.10.1 : les titres de l'accès rapide rétrécissent pour tenir (plus de mot coupé en deux).
- 1.10.2 : toucher à nouveau le morceau en cours le remet au début sans le recharger (plus de requête
  YouTube à chaque tap) ; s'il est en erreur, il est vraiment rechargé.
- 1.10.3 : NewPipeExtractor passe sur le code `dev` du 02/11 (commit `6cb36e9823` via JitPack) : il
  contient le nouveau déchiffrement du paramètre « n » des flux (#1545, #1556) et la version du
  client lue depuis `sw.js_data` (#1520), pas encore publiés dans une version officielle. Avec
  l'ancien déchiffrement, YouTube refusait ou ralentissait les flux (« Source error »). Revenir sur
  une version officielle (`gradle/libs.versions.toml`) dès que la 0.26.6 / 0.27 sort.

### 1.9.0 → 1.9.1

- **Musique, première étape de la refonte** (inspirée des grandes applis de musique, à notre style) :
  - le lecteur prend la couleur de la pochette : dégradé de sa teinte dominante vers la nuit, qui
    glisse d'un morceau à l'autre ; la pilule du dock musique se teinte aussi
    (`ui/components/CoverColor.kt`, calcul maison sans bibliothèque) ;
  - lignes de morceaux plus nettes : titre sur une ligne, petite égalisation animée sur le morceau en
    cours (`PlayingBars`) ;
  - pages album / artiste / playlist : grande pochette sur un halo de sa couleur, titre à gauche,
    gros bouton lecture rond à droite, « Aléatoire » (ou « Mix ») à côté.
  - 1.9.1 : le halo de couleur des pages monte jusqu'en haut de l'écran (plus de coupure sous le bouton retour).

### 1.8.0 → 1.8.1

- **Paysage repensé** :
  - le dock devient une colonne en verre à gauche (même goutte qui glisse, de haut en bas), le contenu
    se décale à sa droite (`FloatingDock(vertical = true)`, `DOCK_RAIL_SPACE`) ;
  - les listes de vidéos passent en plusieurs colonnes selon la largeur (accueil, recherche, chaînes,
    shorts, playlists, abonnements, Bibliothèque) ; en portrait, une seule comme avant
    (`ui/components/AdaptiveList.kt`) ;
  - le dock musique va en bas à droite, plus étroit ; sa fenêtre s'ouvre à droite du dock en colonne,
    en deux parties : la pochette à gauche, titre / commandes à droite (qui défile vers l'artiste et
    l'album).

### 1.7.0

- **Aperçu en glissant sur la barre de lecture** (vidéo, portrait et plein écran) : une vignette de la
  vidéo à l'endroit visé et le temps, au-dessus du curseur. Les images viennent des « storyboards »
  de YouTube (planches de petites images, via NewPipe `previewFrames`), chaque planche téléchargée une
  seule fois (`SeekPreview` dans `ui/components/Video.kt`).

### 1.6.6

- Correction : avec une langue choisie dans les réglages (pas « Système »), l'appli figeait toute la
  configuration du téléphone, orientation comprise : tourner le téléphone ne mettait plus la vidéo en
  plein écran. Seule la langue est maintenant imposée (`AppLocale.overrideConfiguration`).

### 1.6.5

- Barre de progression (vidéo, plein écran, fenêtre musique) : la partie déjà chargée est dessinée en
  blanc transparent devant la position de lecture (`PlayerHolder.bufferedPosition`).

### 1.6.4

- Correction : lancer une vidéo pendant une musique pouvait, une fois la vidéo chargée, relancer le
  morceau suivant de la file en mode vidéo à sa place. Le lecteur était vidé puis rempli (passage
  par « terminé ») : il est maintenant remplacé d'un coup, et une fin de morceau ne relance plus la
  file si autre chose a été lancé entre-temps.

### 1.6.3

- Dock : toucher Musique ramène à l'accueil de Musique (pages d'artiste / d'album / de playlist et
  recherche fermées), comme Accueil, Abonnements et Bibliothèque.

### 1.6.2

- **Morceau précédent gardé** (musique) : le dernier morceau joué reste chargé juste avant celui en
  cours. « Précédent » (dans les 3 premières secondes) ou un tap sur lui dans la file y revient tout de
  suite. Le lecteur garde au plus [précédent] [en cours] [suivant préparé] (`PlayerHolder.kept`).

### 1.6.1

- Correction : ouvrir une page d'artiste dont ni les morceaux ni les albums n'étaient en cache
  (depuis le lecteur, par exemple) faisait planter l'appli (deux « chargement » avec la même clé).

### 1.6.0

- **Fenêtre musique qui défile** : sous les commandes, la carte de l'**artiste** (vers sa page) et celle
  de l'**album** du morceau (vers l'album). L'album est retrouvé grâce à la description que YouTube écrit
  pour les morceaux de YouTube Music (`Music.albumOf`), seulement quand on fait défiler. Tout en haut,
  tirer vers le bas rabat toujours la fenêtre (`MusicPlayerState.scrollConnection`).

### 1.5.0

- **Morceau suivant chargé à l'avance** (musique) : dès qu'un morceau joue, le suivant de la file est
  préparé et placé derrière lui dans ExoPlayer. La transition est instantanée (plus les ~2 s de
  silence), « suivant » aussi. Revu automatiquement si la file, le mode répéter ou le minuteur
  changent (`PlayerHolder.syncPreload`).

### 1.4.0

- Le dock musique ne s'affiche plus que dans l'onglet Musique (la musique continue ailleurs, avec la
  notification pour la piloter).
- Lancer une vidéo pendant une musique la met de côté (morceau, position, file) : au retour dans
  Musique, elle revient en pause là où elle en était (`PlayerHolder.parkedMusic`).
- Inactive 15 min en arrière-plan (rien ne joue, aucun téléchargement), l'appli se vide comme si on
  l'avait retirée des applis récentes ; si le téléphone l'a gelée entre-temps, elle repart de zéro à
  la réouverture (`MainActivity.watchIdle`).

### 1.3.3

- Interrupteur « Lecture automatique » directement sur la vidéo, en haut à droite (et en haut du plein
  écran, à côté des sous-titres) : orange avec ▶ quand c'est activé.

### 1.3.2

- **Lecture automatique** : à la fin d'une vidéo (hors playlist), la première suggestion démarre toute
  seule. Interrupteur « Lecture automatique » dans les réglages du lecteur et dans Réglages › Lecture
  (activé par défaut).

### 1.3.1

- **Clé de publication** : la version release est signée avec la clé de Mikeneko (voir
  « Clé de publication » plus bas) au lieu de la clé de debug. Les APK partagés pourront recevoir
  les mises à jour par-dessus. Un téléphone qui avait une version signée avec la clé de debug doit
  désinstaller une fois (sauvegarder ses données avant : abonnements, historique, bibliothèque).

### 1.2.0 → 1.3.0

- **Bibliothèque** (remplace l'onglet Téléchargés) : historique, « À regarder plus tard », vidéos J'aime
  (le pouce du lecteur), playlists de vidéos perso et téléchargements, tout gardé sur le téléphone.
  Ajouter depuis le menu ⋮ d'une vidéo ou les boutons « Plus tard » / « Playlist » du lecteur
  (`ui/screens/LibraryScreen.kt`, `data/VideoLibrary.kt`, `Playlists.videos`).
- Le menu ⋮ des vidéos marche partout (accueil, chaînes, Bibliothèque, lecteur), plus seulement sur l'accueil.
- Une vidéo lancée depuis « Plus tard », « J'aime » ou une playlist perso s'enchaîne avec les suivantes,
  avec le tiroir de playlist dans le lecteur.

### 1.1.0

- **Radio → Musique** : un vrai lecteur de musique.
  - Recherche YouTube Music (morceaux, albums, artistes, playlists) avec suggestions et historique.
  - Pages album et artiste, Mix auto (la file se prolonge toute seule), 12 ambiances en direct.
  - Playlists perso (glisser pour réordonner), musique téléchargée écoutable hors ligne.
  - Aléatoire, répéter (tout / un), barre de progression, paroles synchronisées (LRCLIB).
  - Suivant / précédent dans la notification et sur l'écran verrouillé.
- **Dock musique en verre liquide** : on tire la pilule vers le haut (ou on la touche) et elle s'étire
  en fenêtre, avec un rebond de gelée ; on la rabat vers le bas pour la ranger.
- **Page de chaîne** : bannière, badge vérifié, description dépliable, onglets Vidéos / Shorts (grille) /
  En direct / Playlists. Page de playlist avec « Tout lire » : les vidéos s'enchaînent toutes seules, et
  dans le lecteur un tiroir en verre qu'on tire vers le haut liste la playlist (les suggestions restent
  sur la page).
- Lecteur vidéo : avatar et nom de la chaîne sous le titre (vers sa page) et « S'abonner » à droite ;
  les boutons apparaissent et s'effacent en fondu, le chargement aussi.
- Toucher une vidéo : sa miniature s'envole jusqu'au lecteur.
- **Volume stable** réservé aux vidéos : coupé pendant la musique, il revient tout seul sur la
  vidéo suivante s'il était activé.
- Abonnements : si le flux RSS de YouTube ne répond plus (404 pour toutes les chaînes le 2026-11-03),
  les vidéos viennent de l'onglet « Vidéos » des chaînes (NewPipe), avec leur durée. Appui long sur
  un avatar, ou « Voir la chaîne » quand une chaîne est sélectionnée, pour ouvrir sa page.
- Dock : toucher Accueil ramène au fil « Pour toi » (recherche fermée, ou en haut s'il y est déjà) ;
  toucher Abonnements ramène à la liste de toutes les chaînes (page de chaîne et filtres fermés).
- Accueil : les rubriques sont en haut de la page et partent avec le défilement.
- Recherche (accueil et musique) : le clavier s'ouvre tout de suite ; ✕ efface le champ sans fermer
  (sur des résultats : retour à la barre, clavier ouvert ; le retour ramène aux résultats) et ne
  ferme la recherche que si le champ est déjà vide. Refaire défiler la page range les suggestions
  et le clavier, comme le retour. L'historique et les suggestions apparaissent et partent en douceur.
- Appli plus légère : APK de 174 à 59 Mo (un APK par processeur, version release réduite par R8),
  yt-dlp installé seulement si on télécharge, cache des miniatures plafonné.
- Retirer l'appli des applis récentes arrête la musique et la fait vraiment quitter (mémoire libérée,
  tout repart de zéro), sauf pendant un téléchargement.
- **Animations à la même vitesse sur tous les téléphones** : l'appli ignore l'« échelle
  d'animation » des options développeur (seule exception : animations coupées dans l'accessibilité).
  Réglage global : `ANIMATION_SPEED` dans `ui/AnimationSpeed.kt`, à 2 (tout va deux fois plus vite
  que les durées écrites dans le code).

</details>

## ⚔️ Ouvrir le projet

1. Android Studio récent (fin 2026), JDK 17 ou plus.
2. **File › Open** → le dossier `Mikeneko`.
3. Laisse Android Studio synchroniser Gradle (il télécharge Gradle 9.6.0 et les dépendances).
4. Lance sur un téléphone ou un émulateur (Android 8.0 minimum, **Android 13+ pour le vrai liquid glass**).

En ligne de commande : `./gradlew assembleRelease`, puis
`adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk` (signée avec la clé de publication,
voir plus bas). Un APK par processeur : `arm64-v8a` pour les téléphones,
`x86_64` pour l'émulateur ; pas de 32 bits.

### 🗝️ Clé de publication ψ(｀∇´)ψ

La version release est signée avec la clé de publication de Mikeneko, **hors du projet** :
`~/.config/mikeneko/mikeneko-release.jks` et son mot de passe dans `~/.config/mikeneko/signing.properties`
(chemin modifiable avec la variable `MIKENEKO_SIGNING`). **Sauvegarde ces deux fichiers** (clé USB,
gestionnaire de mots de passe) : sans eux, impossible de publier une mise à jour qui s'installe par-dessus.
Sans ce fichier, la release est signée avec la clé de debug.
Empreinte SHA-256 : `0F:0B:6E:19:0A:E7:2C:34:63:A0:20:03:61:12:9D:54:F9:F4:B8:6B:CF:DC:70:8A:09:99:3B:03:A7:96:83:DC`.

### 🍱 Poids de l'appli

- APK release arm64 : ~59 Mo (dont ~50 Mo pour python + ffmpeg de yt-dlp, ~8 Mo de code après R8).
  L'APK debug, lui, n'est ni réduit ni compressé (~120 Mo) : il ne sert qu'au développement.
- yt-dlp s'installe (~125 Mo décompressés dans `no_backup/`) seulement à la première ouverture de
  l'onglet Téléchargés, et cherche sa mise à jour au plus une fois par jour.
- Miniatures : cache disque plafonné à 64 Mo (`MikenekoApp.newImageLoader`).
- Les fichiers téléchargés (`Android/data/app.mikeneko/files/Movies` et `Music`) comptent aussi
  dans les « données » de l'appli pour Android.

Toutes les versions sont dans `gradle/libs.versions.toml`. Android Studio te proposera de les
mettre à jour : accepte, surtout pour **NewPipeExtractor** (c'est lui qui suit les changements
de YouTube).

## 🍣 Ce qui est dedans

| Fonction | Où |
| --- | --- |
| Accueil (kiosque NewPipe, recherche avec suggestions et filtres, rubriques) + header flottant en verre | `ui/screens/HomeScreen.kt`, `ui/screens/SearchResults.kt` |
| Abonnements séparés de l'accueil, par ordre chronologique, filtre par chaîne, « Pas vues » ; secours si le RSS de YouTube tombe | `ui/screens/SubscriptionsScreen.kt`, `data/Subscriptions.kt` |
| Pages de chaîne : bannière, onglets Vidéos / Shorts / En direct / Playlists, pages de playlist | `ui/screens/ChannelScreen.kt` |
| Import sans compte : NewPipe (.json), FreeTube (.db), Google Takeout (.csv) | `data/Subscriptions.kt` |
| Lecteur vidéo : boutons en verre sur la vidéo, SponsorBlock auto (« coup de patte »), dislikes, chapitres, chaîne + « S'abonner », lecture automatique | `ui/screens/PlayerScreen.kt`, `FullscreenPlayer.kt`, `PlayerControls.kt`, `player/` |
| Tiroir de playlist dans le lecteur (tirer vers le haut) | `ui/components/PlaylistDrawer.kt` |
| « Marquer un passage » : envoyer sponsors, intros, génériques… à SponsorBlock (anonyme) | `ui/components/SegmentMarker.kt`, `data/CommunityApis.kt` |
| Commentaires (lecture seule, sans compte : réponses, liens et horodatages) | `data/Comments.kt`, `ui/components/CommentsUi.kt` |
| Mini-lecteur PiP dans l'app (vidéo + oreilles de chat) et vrai PiP système en quittant l'app | `ui/components/Video.kt`, `MainActivity.kt` |
| Musique (ex-Radio) : le dock musique (onglet Musique seulement) s'étire en fenêtre de verre liquide (tirer vers le haut / tap), son écran éteint ; une vidéo met la musique de côté | `ui/screens/MusicWindow.kt` (+ `MusicDock`, `MusicPlayerContent`, `MusicQueue`, `MusicPlayerState`), `ui/screens/MusicScreen.kt`, `player/PlaybackService.kt` |
| Musique : recherche YouTube Music (morceaux, albums, artistes, playlists), Mix auto, ambiances | `data/Music.kt`, `ui/screens/MusicPages.kt` (+ `MusicHome`, `MusicSearch`, `MusicPageHeader`) |
| File : enchaînement, aléatoire, répéter, glisser pour réordonner ; suivant/précédent dans la notification | `player/PlayerHolder.kt`, `player/PlaybackService.kt` |
| Playlists perso, musique téléchargée hors ligne, menu ⋮ des morceaux | `data/Playlists.kt`, `data/LocalMusic.kt`, `ui/screens/MusicUi.kt` |
| Paroles synchronisées (LRCLIB) | `data/Lyrics.kt`, `ui/screens/LyricsSheet.kt` |
| Bibliothèque : historique, à regarder plus tard, J'aime, tes shorts, playlists vidéo | `ui/screens/LibraryScreen.kt`, `data/VideoLibrary.kt` |
| Téléchargements yt-dlp (dans la Bibliothèque) : vidéo, MP3, FLAC, Opus, M4A, sponsors coupés | `data/Downloads.kt`, `ui/screens/DownloadsScreen.kt` |
| « T'as vu l'heure ? » après 1 h | `ui/screens/SleepReminder.kt` |
| Réglages : langue, région des contenus, lecture (dont lecture automatique), SponsorBlock, historique, rappel dodo, sauvegarde, mises à jour | `ui/screens/SettingsScreen.kt`, `settings/` |
| Cycle de vie : retirer l'appli des récentes ou 15 min d'inactivité en arrière-plan la vident | `player/PlaybackService.kt`, `MainActivity.kt` |
| « Pour toi » : algorithme local (historique + suggestions YouTube + abonnements) | `data/ForYou.kt`, `data/WatchHistory.kt` |
| Partager un lien YouTube vers Mikeneko / ouvrir les liens YouTube | `AndroidManifest.xml` |

### ⚡ Vitesse des animations (mode chidori)

`MainActivity` passe à `setContent` son propre recomposeur (`fixedSpeedRecomposer()` dans
`ui/AnimationSpeed.kt`) avec une `MotionDurationScale` fixe. Toutes les animations Compose (y compris
dans les menus et popups) vont à la même vitesse quel que soit le réglage d'échelle d'animation du
téléphone. `ANIMATION_SPEED = 2` : une animation écrite `tween(400)` dure en vrai 200 ms (pareil pour
les ressorts) ; en tenir compte quand on règle une durée. Attention : `delay()` n'a jamais suivi cette échelle, donc ne
jamais caler une étape d'animation sur un `delay()`.

### 🔮 Le liquid glass ✧･ﾟ

`ui/glass/LiquidGlass.kt` :

- Le contenu de l'écran est enregistré dans un `GraphicsLayer` (le « backdrop »).
- Chaque morceau de verre le redessine à travers un **shader AGSL** (Android 13+) : bord bombé
  façon squircle, réfraction vers l'intérieur, petite frange arc-en-ciel, reflet de bord.
- Android 12 : verre dépoli (flou). Android 8 à 11 : verre teinté simple.
- La vidéo utilise une `TextureView` (`res/layout/player_texture.xml`) pour que le verre puisse
  la réfracter.
- Règle d'or : un `LiquidGlass` ne doit jamais être **dans** la source qu'il réfracte. C'est pour ça
  que chaque écran est coupé en `…Content` (ce qui défile) et `…Overlay` (ce qui est en verre).

Le dock musique et sa fenêtre sont une seule vitre (`MusicDockWindow`) dont la taille suit `MusicPlayerState.expand`
(0 = pilule, 1 = fenêtre) : le doigt la tire, un ressort la pose. Le contenu de la fenêtre est construit à sa taille finale
et seulement découpé par la vitre, pour ne pas remettre en page tout le lecteur à chaque image.

## 🗾 Langues

L'appli est en français et en anglais (l'anglais sert de langue par défaut pour tous les autres
pays). Le choix se fait dans **Réglages › Langue** ; « Langue du téléphone » suit le système.

Pour ajouter une langue (par exemple l'espagnol) :

1. Copie `app/src/main/res/values/strings.xml` (anglais) vers `app/src/main/res/values-es/strings.xml`
   et traduis chaque texte (garde les `%1$s`, `%1$d` : ce sont les valeurs insérées).
2. Ajoute `"es" to "Español"` dans `AppLocale.LANGUAGES` (`settings/AppLocale.kt`).

Les recherches des rubriques de l'accueil (`query_*`) sont aussi des textes traduits : ce sont
elles qui donnent des vidéos dans la bonne langue.

## 🌑 Pas de compte Google (￢_￢)

La connexion Google (OAuth + YouTube Data API) a été retirée au grand nettoyage (2.1.3), avec les
Play Services : elle n'avait jamais pu être activée sans déclarer l'app dans la Google Cloud Console.
Les abonnements s'importent depuis NewPipe, FreeTube ou Google Takeout (Réglages › Sauvegarde) ;
les commentaires se lisent sans compte. Pour la ressortir : `data/GoogleAccount.kt` et la
procédure de la console sont dans l'historique git (commit `066b0c2`).

## 🎋 Structure

```
app/src/main/java/app/mikeneko/
├── MikenekoApp.kt          init NewPipe, cache des miniatures (yt-dlp s'installe à la demande)
├── MainActivity.kt         edge-to-edge, liens partagés, PiP système, plein écran, nettoyage après 15 min
├── data/                   NewPipe, SponsorBlock, RYD, abonnements, Bibliothèque, musique, téléchargements
├── player/                 ExoPlayer partagé, sources Media3, service de lecture
└── ui/
    ├── MikenekoRoot.kt     navigation, dock, mini-PiP
    ├── AnimationSpeed.kt   vitesse des animations identique sur tous les téléphones
    ├── glass/              liquid glass (AGSL)
    ├── components/         dock, capsules, cartes vidéo, menu ⋮, tiroir de playlist, mini-PiP
    ├── icons/              icônes de la maquette
    ├── screens/            Accueil, Abonnements, Chaînes, Musique (MusicScreen, MusicWindow…), Bibliothèque, Lecteur, Dodo
    └── theme/              palette « pelage » + police M PLUS Rounded 1c
```

## 🌠 Prochaines étapes possibles

- Couleurs du lecteur tirées de la miniature (`androidx.palette`).

## 🙇 Crédits : arigatō !

- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0) : c'est pourquoi
  Mikeneko est aussi sous GPL-3.0.
- [SponsorBlock](https://sponsor.ajay.app), [Return YouTube Dislike](https://returnyoutubedislike.com).
- [youtubedl-android](https://github.com/yausername/youtubedl-android) / yt-dlp.
- Police **M PLUS Rounded 1c** (The M+ FONTS Project Authors), SIL Open Font License 1.1,
  réduite au latin, aux kanas et à « 三毛猫 ».

---

<div align="center">
<i>« Je ne regarde pas les pubs. Je les tranche. »</i> 🗡️🌑<br><br>
<sub>ฅ^•ﻌ•^ฅ 三毛猫 <i>mikeneko</i> : « chat tricolore » en japonais · logiciel libre sous GPL-3.0</sub>
</div>
