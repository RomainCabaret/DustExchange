# DustExchange

DustExchange est un plugin d'économie et de marché asynchrone pour les serveurs Minecraft. Il isole les transactions de base de données hors du thread principal afin de prévenir les baisses de performances (TPS) lors des accès réseau.

## Prérequis

* Nécessite CraftBukkit, Spigot ou Paper pour fonctionner.  
* Nécessite Java 8 ou supérieur.  
* Un serveur Redis fonctionnel.
* Un plugin d'économie compatible Vault.

## Architecture Technique

Le code est structuré pour garantir l'intégrité des données sans impacter la boucle principale du serveur :

* **Opérations Asynchrones** : 
Les modifications de stock et la lecture des données depuis Redis sont exécutées sur des threads secondaires via ` CompletableFuture`.
* **Synchronisation Bukkit** : Les transactions monétaires et les modifications d'inventaire sont réintégrées de manière stricte sur le Main Thread via `Bukkit.getScheduler().runTask()`.
* **Verrouillage (Thread-Safety)**: Les joueurs sont verrouillés individuellement par leur UUID via un `ConcurrentHashMap.newKeySet()` pendant la durée totale d'un cycle de transaction, bloquant physiquement les requêtes concurrentes et les race conditions.
* **Système de Claim Box** : Les objets achetés ou remboursés qui ne peuvent pas être ajoutés à un inventaire plein sont automatiquement sérialisés et stockés de manière persistante sur Redis.

## Intégration PlaceholderAPI (PAPI)

DustExchange expose les données du marché en temps réel pour une intégration native dans vos scoreboards, hologrammes ou menus externes. 

* `%dustexchange_price_buy_<item>%`: Retourne le prix d'achat actuel d'un matériau.
* `%dustexchange_price_sell_<item>%` : Retourne le prix de rachat actuel.
* `%dustexchange_stock_<item>%` : Retourne le volume de stock disponible.
* `%dustexchange_pending_claims%` : Retourne le nombre d'objets en attente dans la Claim Box du joueur.

## Installation

1. Téléchargez le fichier `.jar` de la dernière version.
2. Placez le fichier dans le dossier `plugins/` de votre serveur.
3. Démarrez le serveur pour générer les fichiers de configuration.
4. Renseignez les identifiants de votre base de données Redis dans le fichier `config.yml`.
5. Redémarrez le serveur.

## Compilation depuis les sources

Pour compiler DustExchange, vous devez avoir un Java Development Kit (JDK) installé pour Java 8 ou une version supérieure.  

Clonez ce dépôt, puis exécutez la commande suivante :
* Sur Linux ou macOS : exécutez `./gradlew build`. 
* Sur Windows : exécutez `gradlew build`. 

Une fois l'opération terminée, le fichier `.jar` compilé se trouvera dans le dossier `build/libs/`.

## Commandes et Permissions

* `/market` : Ouvre l'interface graphique du marché et permet d'accéder à l'Ender Chest de récupération (Claim Box).
    * Permission par défaut : `dustexchange.useDustExchange`
