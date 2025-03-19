import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javax.net.ssl.*;
import java.io.*;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

// Classe principale du serveur de contrôle à distance
public class ServeurControleDistance extends Application {
    // Indicateur d'état du serveur (actif ou non)
    private boolean estActif = false;
    // Liste synchronisée des gestionnaires de clients connectés
    private final List<GestionnaireClient> listeClients = Collections.synchronizedList(new ArrayList<>());
    // Socket SSL pour écouter les connexions entrantes
    private SSLServerSocket socketServeur;
    // Zone de texte pour afficher les journaux (logs)
    private TextArea zoneJournal;
    // Liste pour afficher les clients connectés dans l'interface
    private ListView<String> vueListeClients;
    // Boutons pour démarrer et arrêter le serveur
    private Button boutonDemarrer, boutonArreter;
    // Écrivain pour le fichier de journalisation
    private PrintWriter ecrivainFichierJournal;
    // Map pour stocker les utilisateurs et leurs mots de passe (authentification)
    private final HashMap<String, String> utilisateurs = new HashMap<>();

    // Constructeur : initialise les utilisateurs pour l'authentification
    public ServeurControleDistance() {
        utilisateurs.put("admin", "password123");
    }

    // Point d'entrée de l'application
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage fenetrePrincipale) {
        // Définit le titre de la fenêtre principale
        fenetrePrincipale.setTitle("Serveur de Contrôle à Distance - ESP Dakar");

        // Initialisation du fichier de journalisation avec encodage UTF-8
        try {
            ecrivainFichierJournal = new PrintWriter(new OutputStreamWriter(new FileOutputStream("journal_serveur.txt", true), StandardCharsets.UTF_8), true);
        } catch (IOException e) {
            System.err.println("Erreur création fichier journal : " + e.getMessage());
        }

        // Configuration de la zone de journalisation (non éditable)
        zoneJournal = new TextArea();
        zoneJournal.setEditable(false);
        zoneJournal.setPrefHeight(300);
        zoneJournal.setPromptText("Journal des événements...");

        // Configuration de la liste des clients connectés
        vueListeClients = new ListView<>();
        vueListeClients.setPrefHeight(100);
        vueListeClients.setPlaceholder(new Label("Aucun client connecté"));

        // Initialisation des boutons de démarrage et d'arrêt
        boutonDemarrer = new Button("Démarrer le Serveur");
        boutonArreter = new Button("Arrêter le Serveur");
        boutonArreter.setDisable(true);

        // Actions des boutons
        boutonDemarrer.setOnAction(e -> demarrerServeur());
        boutonArreter.setOnAction(e -> arreterServeur());

        // Mise en page de l'interface graphique
        VBox miseEnPage = new VBox(10, new Label("Clients connectés:"), vueListeClients, new Label("Journal:"), zoneJournal, boutonDemarrer, boutonArreter);
        miseEnPage.setPadding(new javafx.geometry.Insets(10));
        fenetrePrincipale.setScene(new Scene(miseEnPage, 600, 500));

        // Gestion de la fermeture de la fenêtre
        fenetrePrincipale.setOnCloseRequest(e -> {
            arreterServeur();
            Platform.exit();
            System.exit(0);
        });
        fenetrePrincipale.show();
    }

    // Méthode pour démarrer le serveur
    private void demarrerServeur() {
        if (estActif) return;
        estActif = true;
        boutonDemarrer.setDisable(true);
        boutonArreter.setDisable(false);

        // Lancement du serveur dans un thread séparé
        new Thread(() -> {
            try {
                // Configuration du keystore pour SSL
                System.setProperty("javax.net.ssl.keyStore", "server.keystore");
                System.setProperty("javax.net.ssl.keyStorePassword", "changeit");
                SSLServerSocketFactory fabrique = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
                socketServeur = (SSLServerSocket) fabrique.createServerSocket(1234);
                journaliser("Serveur SSL démarré sur le port 1234...");

                // Boucle pour accepter les connexions des clients
                while (estActif) {
                    try {
                        SSLSocket socketClient = (SSLSocket) socketServeur.accept();
                        GestionnaireClient gestionnaireClient = new GestionnaireClient(socketClient);
                        synchronized (listeClients) {
                            listeClients.add(gestionnaireClient);
                        }
                        Platform.runLater(() -> vueListeClients.getItems().add(socketClient.getRemoteSocketAddress().toString()));
                        gestionnaireClient.start();
                    } catch (SocketException se) {
                        if (estActif) journaliser("Erreur de socket : " + se.getMessage());
                        break;
                    }
                }
            } catch (IOException e) {
                journaliser("Erreur démarrage serveur : " + e.getMessage());
            }
        }).start();
    }

    // Méthode pour arrêter le serveur
    private void arreterServeur() {
        if (!estActif) return;
        estActif = false;
        boutonDemarrer.setDisable(false);
        boutonArreter.setDisable(true);

        // Déconnexion de tous les clients
        synchronized (listeClients) {
            for (GestionnaireClient client : listeClients) {
                client.fermerConnexion();
            }
            listeClients.clear();
        }
        Platform.runLater(() -> vueListeClients.getItems().clear());

        // Fermeture du socket serveur
        try {
            if (socketServeur != null && !socketServeur.isClosed()) {
                socketServeur.close();
            }
            journaliser("Serveur arrêté.");
        } catch (IOException e) {
            journaliser("Erreur arrêt serveur : " + e.getMessage());
        }
        if (ecrivainFichierJournal != null) ecrivainFichierJournal.close();
    }

    // Méthode pour journaliser un message dans l'interface et le fichier
    private void journaliser(String message) {
        String messageFormate = String.format("[%tT] %s", new java.util.Date(), message);
        Platform.runLater(() -> zoneJournal.appendText(messageFormate + "\n"));
        if (ecrivainFichierJournal != null) {
            ecrivainFichierJournal.println(messageFormate);
        }
    }

    // Classe interne pour gérer chaque client connecté
    class GestionnaireClient extends Thread {
        private final SSLSocket socket;
        private BufferedReader entree;
        private PrintWriter sortie;
        private boolean estAuthentifie = false;

        // Constructeur : initialise les flux d'entrée/sortie pour le client
        public GestionnaireClient(SSLSocket socket) {
            this.socket = socket;
            try {
                entree = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                sortie = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            } catch (IOException e) {
                journaliser("Erreur initialisation client : " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                journaliser("Nouveau client connecté : " + socket.getRemoteSocketAddress());
                sortie.println("Veuillez vous authentifier (login:motdepasse)");

                // Authentification du client
                String identifiants = entree.readLine();
                if (identifiants != null && authentifier(identifiants)) {
                    estAuthentifie = true;
                    sortie.println("Connexion établie avec le serveur");
                    journaliser("Client authentifié : " + socket.getRemoteSocketAddress());

                    // Boucle pour recevoir et exécuter les commandes du client
                    String commande;
                    while ((commande = entree.readLine()) != null && estActif && estAuthentifie) {
                        journaliser("Commande reçue de " + socket.getRemoteSocketAddress() + " : " + commande);
                        executerCommande(commande);
                    }
                } else {
                    sortie.println("ERROR:Authentification échouée");
                    journaliser("Échec authentification : " + socket.getRemoteSocketAddress());
                }
            } catch (IOException e) {
                journaliser("Erreur communication avec " + socket.getRemoteSocketAddress() + " : " + e.getMessage());
            } finally {
                fermerConnexion();
            }
        }

        // Méthode pour authentifier un client
        private boolean authentifier(String identifiants) {
            String[] parties = identifiants.split(":");
            if (parties.length != 2) return false;
            String login = parties[0];
            String motDePasse = parties[1];
            return utilisateurs.containsKey(login) && utilisateurs.get(login).equals(motDePasse);
        }

        // Méthode pour exécuter une commande reçue du client
        private void executerCommande(String commande) {
            if (commande.startsWith("UPLOAD:")) {
                String nomFichier = commande.substring(7);
                recevoirFichier(nomFichier);
            } else if (commande.startsWith("DOWNLOAD:")) {
                String nomFichier = commande.substring(9);
                envoyerFichier(nomFichier);
            } else {
                try {
                    ProcessBuilder pb;
                    if (System.getProperty("os.name").toLowerCase().contains("win")) {
                        // Sur Windows, forcer l'encodage UTF-8
                        pb = new ProcessBuilder("cmd.exe", "/c", "chcp 65001 > nul && " + commande);
                    } else {
                        // Sur Linux/Unix, utiliser /bin/sh
                        pb = new ProcessBuilder("/bin/sh", "-c", commande);
                    }
                    pb.redirectErrorStream(true);
                    pb.environment().put("LC_ALL", "en_US.UTF-8");
                    Process processus = pb.start();

                    // Lire la sortie de la commande avec encodage UTF-8
                    BufferedReader lecteur = new BufferedReader(new InputStreamReader(processus.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder resultat = new StringBuilder();
                    String ligne;
                    while ((ligne = lecteur.readLine()) != null) {
                        resultat.append(ligne).append("\n");
                    }
                    processus.waitFor();

                    sortie.println("RESULT:" + resultat.toString());
                    sortie.flush();
                    journaliser("Résultat envoyé à " + socket.getRemoteSocketAddress());
                } catch (Exception e) {
                    sortie.println("ERROR:Erreur lors de l'exécution : " + e.getMessage());
                    sortie.flush();
                    journaliser("Erreur exécution pour " + socket.getRemoteSocketAddress() + " : " + e.getMessage());
                }
            }
        }

        // Méthode pour recevoir un fichier envoyé par le client
        private void recevoirFichier(String nomFichier) {
            try {
                File dossier = new File("server_files");
                if (!dossier.exists()) dossier.mkdir();
                FileOutputStream fluxSortieFichier = new FileOutputStream("server_files/" + nomFichier);
                byte[] tampon = new byte[1024];
                int octetsLus;
                InputStream fluxEntree = socket.getInputStream();
                while ((octetsLus = fluxEntree.read(tampon)) != -1) {
                    fluxSortieFichier.write(tampon, 0, octetsLus);
                    if (octetsLus < tampon.length) break;
                }
                fluxSortieFichier.close();
                journaliser("Fichier reçu : " + nomFichier);
                sortie.println("RESULT:Fichier uploadé avec succès");
            } catch (IOException e) {
                sortie.println("ERROR:Erreur upload : " + e.getMessage());
                journaliser("Erreur réception fichier : " + e.getMessage());
            }
        }

        // Méthode pour envoyer un fichier au client
        private void envoyerFichier(String nomFichier) {
            try {
                File fichier = new File("server_files/" + nomFichier);
                if (!fichier.exists()) {
                    sortie.println("ERROR:Fichier introuvable");
                    journaliser("Fichier introuvable : " + nomFichier);
                    return;
                }
                FileInputStream fluxEntreeFichier = new FileInputStream(fichier);
                OutputStream fluxSortie = socket.getOutputStream();
                byte[] tampon = new byte[1024];
                int octetsLus;
                while ((octetsLus = fluxEntreeFichier.read(tampon)) != -1) {
                    fluxSortie.write(tampon, 0, octetsLus);
                }
                fluxEntreeFichier.close();
                fluxSortie.flush();
                journaliser("Fichier envoyé : " + nomFichier);
                sortie.println("RESULT:Fichier téléchargé avec succès");
            } catch (IOException e) {
                sortie.println("ERROR:Erreur download : " + e.getMessage());
                journaliser("Erreur envoi fichier : " + e.getMessage());
            }
        }

        // Méthode pour fermer la connexion avec le client
        public void fermerConnexion() {
            synchronized (listeClients) {
                listeClients.remove(this);
            }
            Platform.runLater(() -> vueListeClients.getItems().remove(socket.getRemoteSocketAddress().toString()));
            try {
                if (entree != null) entree.close();
                if (sortie != null) sortie.close();
                if (socket != null && !socket.isClosed()) socket.close();
                journaliser("Client déconnecté : " + socket.getRemoteSocketAddress());
            } catch (IOException e) {
                journaliser("Erreur fermeture client : " + e.getMessage());
            }
        }
    }
}