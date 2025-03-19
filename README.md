Système de Contrôle à Distance - ESP Dakar
Ce projet est une application client-serveur de contrôle à distance qui permet d'exécuter des commandes et de transférer des fichiers de manière sécurisée entre deux machines. L'application est développée en Java avec JavaFX pour l'interface graphique et utilise SSL pour sécuriser les communications.
Fonctionnalités
Serveur

Interface graphique pour la gestion des connexions
Authentification des clients par login/mot de passe
Exécution de commandes système à distance
Transfert de fichiers bidirectionnel (upload/download)
Journalisation des activités (interface et fichier)
Communications chiffrées via SSL

Client

Interface graphique intuitive
Connexion sécurisée SSL au serveur
Exécution de commandes à distance
Upload et téléchargement de fichiers
Historique des commandes
Support complet d'encodage UTF-8

Prérequis

Java 8 ou supérieur
JavaFX
Un keystore SSL configuré pour le serveur

Configuration du SSL
Avant de lancer l'application, vous devez générer un keystore SSL :
bashCopierkeytool -genkeypair -alias server -keyalg RSA -keysize 2048 -keystore server.keystore -validity 365
Le mot de passe par défaut utilisé dans l'application est "changeit". Si vous utilisez un mot de passe différent, modifiez-le dans le code source.
Démarrer le serveur

Compilez le code source :
bashCopierjavac ServeurControleDistance.java

Exécutez le serveur :
bashCopierjava ServeurControleDistance

Cliquez sur le bouton "Démarrer le Serveur" dans l'interface

Utiliser le client

Compilez le code source :
bashCopierjavac ClientControleDistance.java

Exécutez le client :
bashCopierjava ClientControleDistance

Entrez les détails de connexion :

Hôte : L'adresse IP du serveur (localhost par défaut)
Port : Le port du serveur (1234 par défaut)
Login : Nom d'utilisateur (admin par défaut)
Mot de passe : Le mot de passe (password123 par défaut)


Cliquez sur "Se connecter"

Utilisation
Exécuter des commandes à distance

Entrez une commande système dans le champ "Commande"
Cliquez sur "Envoyer" ou appuyez sur Entrée
Le résultat s'affichera dans la liste des résultats

Uploader un fichier vers le serveur

Cliquez sur "Uploader Fichier"
Sélectionnez un fichier dans le sélecteur de fichiers
Le fichier sera transféré au serveur dans le dossier "server_files"

Télécharger un fichier depuis le serveur

Entrez le nom du fichier à télécharger dans le champ "Commande"
Cliquez sur "Télécharger Fichier"
Le fichier sera téléchargé avec le préfixe "downloaded_"

Structure du projet
Serveur

ServeurControleDistance.java : Application principale du serveur
GestionnaireClient : Classe interne pour gérer les clients connectés

Client

ClientControleDistance.java : Application principale du client

Sécurité

Toutes les communications sont chiffrées via SSL
Authentification obligatoire des clients
Exécution des commandes dans un environnement contrôlé
Journalisation complète de toutes les activités

Limitations actuelles et améliorations possibles

Authentification basique (login/mot de passe en clair)
Interface de gestion des utilisateurs non implémentée
Pas de limitation sur les commandes qui peuvent être exécutées
Gestion des transferts de fichiers volumineux à améliorer

Notes techniques

Adaptation automatique à l'environnement Windows/Linux pour l'exécution des commandes
Démarrage des commandes dans des processus distincts pour éviter le blocage de l'application