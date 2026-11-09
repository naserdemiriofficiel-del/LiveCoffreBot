# Live Coffre Bot

Pose tes Coffres au trésor au hasard et relance le Sac-cadeau pendant tes lives TikTok, sans dépasser ton budget.

## Installer (une seule fois)
1. Sur ton PC, installe **Android Studio** (gratuit) : https://developer.android.com/studio
2. Décompresse ce dossier, puis dans Android Studio : **File → Open** → choisis le dossier `LiveCoffreBot`.
   Laisse-le télécharger ce qu'il faut (quelques minutes la première fois).
3. Sur ton téléphone : Paramètres → À propos → Infos logiciel → appuie 7 fois sur « Numéro de version »,
   puis dans « Options de développement » active **Débogage USB**.
4. Branche le téléphone en USB, choisis-le en haut d'Android Studio et appuie sur ▶ (Run).

## Utiliser
1. Ouvre l'appli **Live Coffre Bot**, règle ton budget et tes temps, puis appuie sur **Enregistrer**.
2. Appuie sur « Activer le service d'accessibilité » → Applications installées → **Live Coffre Bot** → Activer.
   (Sur Samsung, si c'est grisé : Paramètres → Applications → Live Coffre Bot → ⋮ → « Autoriser les paramètres restreints ».)
3. Lance ton live. **La première fois, appuie sur 🎯 (apprentissage)** et touche les boutons que la bulle te demande
   (Interagir → Coffre au trésor → montant → Envoyer → Sac-cadeau → Envoyer). Pour les deux « Envoyer »,
   le bot retient seulement la position, rien n'est dépensé.
4. La bulle (horizontale) : **▶** démarre / **⏸** pause, **🎯** apprentissage, **✕** arrête tout et ferme le bot, **✥** pour la déplacer.
5. Le bot s'arrête tout seul quand le budget est atteint.

## Sécurités
- N'agit que dans TikTok, et seulement après ▶.
- Ne touche JAMAIS au « Coffre de Super Fan » ni à un écran de paiement (CHF, €, Recharger…) : il referme et note l'alerte.
- Ne fait jamais deux « Retour » rapprochés (TikTok quitterait le live).
- S'arrête après 4 échecs d'affilée.

## Si ça bloque
Pendant le live, fais un **appui long sur le texte** de la bulle (le compteur), puis dans l'appli **Partager le diagnostic** et envoie-moi le fichier.
