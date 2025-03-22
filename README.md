# Membres du groupe:
- Simon Ezéchiel KAMATE
- Massina Sylvanus BASSENE
- Assietou NDIAYE

Classe: **M1 GLSI**


# Logiciel de Contrôle à Distance en Java

## Description
Ce projet est une implémentation d'un logiciel de contrôle à distance développé dans le cadre du module "Java Avancé" à l'École Supérieure Polytechnique de Dakar (ESP Dakar) pour le Master 1 GLSI sous la direction de Dr. Mouhamed DIOP. Il permet à un client d'exécuter des commandes à distance sur un serveur, de transférer des fichiers, et de gérer plusieurs connexions simultanées via une interface graphique développée avec JavaFX.

## Fonctionnalités
### Côté Client
- **Connexion sécurisée** : Connexion au serveur via SSL/TLS avec authentification (login/mot de passe).
- **Exécution de commandes** : Envoi de commandes système (ex. `dir` sous Windows, `ls` sous Linux) et affichage des résultats.
- **Transfert de fichiers** :
  - **Upload** : Envoi de fichiers vers le serveur.
  - **Download** : Réception de fichiers depuis le serveur, stockés dans le dossier local `client_files`.
- **Interface graphique** :
  - Champs pour saisir l'hôte, le port, le login et le mot de passe.
  - Boutons pour se connecter/déconnecter, envoyer des commandes, uploader et télécharger des fichiers.
  - Liste des résultats et historique des commandes avec réutilisation par double-clic.
  - Indicateur de statut (connecté/déconnecté).

### Côté Serveur
- **Gestion multi-clients** : Supporte plusieurs connexions simultanées grâce à des threads.
- **Authentification** : Vérifie les identifiants des clients avant de permettre l'accès.
- **Exécution de commandes** : Exécute les commandes reçues et renvoie les résultats.
- **Transfert de fichiers** :
  - Réception de fichiers dans le dossier `server_files`.
  - Envoi de fichiers demandés par les clients.
- **Interface graphique** :
  - Liste des clients connectés avec leurs adresses.
  - Journal des événements (connexions, commandes, erreurs) affiché et sauvegardé dans `journal_serveur.txt`.
  - Boutons pour démarrer/arrêter le serveur.

## Prérequis
- **Java** : Version 8 ou supérieure.
- **JavaFX** : Inclus dans le JDK ou configuré séparément si nécessaire.
- **Keystores SSL** :
  - `server.keystore` pour le serveur.
  - `client.keystore` pour le client.
  - Mot de passe par défaut : `changeit`.

## Installation
1. **Cloner le dépôt** :
   ```bash
   git clone <https://github.com/simonkamate/ProjetReseau>


Compiler et exécuter :
Serveur : javac ServeurControleDistance.java
java ServeurControleDistance

Client : javac ClientControleDistance.java
java ClientControleDistance

Configurer les keystores :Placez server.keystore et client.keystore dans le répertoire racine du projet.
Générez-les avec keytool si nécessaire (voir la section "Configuration SSL").

Utilisation

Démarrer le serveur :
Lancez ServeurControleDistance.
Cliquez sur "Démarrer le Serveur". Le serveur écoute sur le port 1234.
Connecter le client :
Lancez ClientControleDistance.
Saisissez l'hôte (ex. localhost), le port (ex. 1234), le login (admin) et le mot de passe (password123).
Cliquez sur "Se connecter".
Envoyer une commande :
Tapez une commande dans le champ "Commande" (ex. dir ou ls) et cliquez sur "Envoyer".
Transférer des fichiers :
Upload : Cliquez sur "Uploader Fichier", choisissez un fichier et validez.
Download : Saisissez le nom du fichier dans le champ "Commande" et cliquez sur "Télécharger Fichier".
Déconnexion : Cliquez sur "Se déconnecter" ou fermez la fenêtre.

Configuration SSL
Pour générer les keystores :

Keystore serveur :keytool -genkeypair -alias server -keyalg RSA -keystore server.keystore -storepass changeit
Keystore client :keytool -genkeypair -alias client -keyalg RSA -keystore client.keystore -storepass changeit

Exporter et importer les certificats (facultatif pour une confiance mutuelle).
Structure du Projet
ClientControleDistance.java : Code source du client.
ServeurControleDistance.java : Code source du serveur.
client_files/ : Dossier où les fichiers téléchargés sont stockés.
server_files/ : Dossier où les fichiers uploadés sont stockés.
journal_serveur.txt : Fichier de logs du serveur.