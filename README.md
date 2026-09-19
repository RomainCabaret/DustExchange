# DustExchange

DustExchange est un plugin d'économie et de marché asynchrone pour les serveurs Minecraft. Il isole les transactions de base de données hors du thread principal afin de prévenir les baisses de performances (TPS) lors des accès réseau.

## Prérequis

* Nécessite CraftBukkit, Spigot ou Paper pour fonctionner.  
* Nécessite Java 8 ou supérieur.  
* Un serveur Redis fonctionnel.
* Un plugin d'économie compatible Vault.

## Installation

1. Téléchargez le fichier `.jar` de la dernière version.
2. Placez le fichier dans le dossier `plugins/` de votre serveur.
3. Démarrez le serveur pour générer les fichiers de configuration.
4. Renseignez les identifiants de votre base de données Redis dans le fichier `config.yml`.
5. Redémarrez le serveur.

## Commandes et Permissions

### Joueurs
* `/market` (alias : `/bourse`) : Ouvre l'interface graphique du marché et la Claim Box.
    * Permission : `dustexchange.useDustExchange`

### Administration
*Permission requise : `dustexchange.admin`*

* `/market add <prix_base> <stock_base> <slot>` : Ajoute l'objet tenu en main principale au marché. Le prix et le stock de base définissent le point d'équilibre de l'inflation.
* `/market remove <slot>` : Supprime définitivement l'objet présent au slot indiqué (supprime aussi son stock dynamique de Redis).
* `/market toggle <slot>` : Active ou désactive instantanément les transactions sur un objet spécifique (utile pour geler le marché d'un item sans le supprimer).


## Architecture Technique

Le code est structuré pour garantir l'intégrité des données sans impacter la boucle principale du serveur :

* **Opérations Asynchrones** : 
Les modifications de stock et la lecture des données depuis Redis sont exécutées sur des threads secondaires via ` CompletableFuture`.
* **Synchronisation Bukkit** : Les transactions monétaires et les modifications d'inventaire sont réintégrées de manière stricte sur le Main Thread via `Bukkit.getScheduler().runTask()`.
* **Verrouillage (Thread-Safety)**: Les joueurs sont verrouillés individuellement par leur UUID via un `ConcurrentHashMap.newKeySet()` pendant la durée totale d'un cycle de transaction, bloquant physiquement les requêtes concurrentes et les race conditions.
* **Système de Claim Box** : Les objets achetés ou remboursés qui ne peuvent pas être ajoutés à un inventaire plein sont automatiquement sérialisés et stockés de manière persistante sur Redis.

## Intégration PlaceholderAPI (PAPI)

DustExchange expose les données du marché en temps réel pour une intégration native dans vos scoreboards, hologrammes ou menus externes. Les objets sont ciblés par leur numéro de **slot** dans l'interface.

* `%dustexchange_buyprice_<slot>%` : Retourne le prix d'achat actuel de l'objet.
* `%dustexchange_sellprice_<slot>%` : Retourne le prix de rachat actuel.
* `%dustexchange_stock_<slot>%` : Retourne le volume de stock disponible.

*Exemple : `%dustexchange_buyprice_10%` affichera le prix de l'objet situé au slot 10.*


## Compilation depuis les sources

Pour compiler DustExchange, vous devez avoir un Java Development Kit (JDK) installé pour Java 8 ou une version supérieure.  

Clonez ce dépôt, puis exécutez la commande suivante :
* Sur Linux ou macOS : exécutez `./gradlew build`. 
* Sur Windows : exécutez `gradlew build`. 

Une fois l'opération terminée, le fichier `.jar` compilé se trouvera dans le dossier `build/libs/`.
