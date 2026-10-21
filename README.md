# BoostPlugin (Paper 26.3)

Plugin serveur uniquement : les joueurs n'ont RIEN a installer.

## Compiler
Il faut JDK 25.
- IntelliJ IDEA : "Open" le dossier, il lit build.gradle, puis Gradle > Tasks > build > build.
- ou en ligne de commande avec Gradle installe : `gradle build`

Le jar est dans `build/libs/BoostPlugin-1.0.0.jar`.

## Installer
Mets le jar dans le dossier `plugins/` du serveur (sur Aternos : onglet Plugins / Fichiers),
sur un serveur Paper 26.3, puis redemarre.

## Tester vite (admin)
/boostgive <joueur> doll
/boostgive <joueur> fragment zephyr
/boostgive <joueur> charm zephyr   (amulette deja liee au joueur)
Boosts : vein, zephyr, titan, skin, abyss, green, phoenix

## Recettes
- Poupee : fer/laine aux coins, totem au centre, coeur de la mer en bas (IWI / WTW / ICI). Liee a celui qui la craft.
- 9 fragments identiques = Pierre de boost
- Template : DCD / DND / DDD (D diamant, N bloc de netherite, C pierre de boost)
- Table de forgeron : template + poupee + lingot de netherite = Amulette liee au proprietaire de la poupee
- Les fragments se trouvent dans les coffres des manoirs, ruines sous-marines, anciennes cites.

## Si la compilation echoue
Copie-moi l'erreur exacte, je corrige. Points les plus probables :
- la version de paper-api dans build.gradle (voir docs.papermc.io)
- `api-version` dans src/main/resources/plugin.yml
- un nom de particule/son renomme dans la 26.x
