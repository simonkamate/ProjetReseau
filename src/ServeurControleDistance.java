import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javax.net.ssl.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class ServeurControleDistance extends Application {
    // Variables pour la gestion du serveur
    private boolean estActif = false; // Indicateur de l'état du serveur
    private final List<GestionnaireClient> listeClients = Collections.synchronizedList(new ArrayList<>()); // Liste des clients connectés
    private SSLServerSocket socketServeur; // Socket sécurisé pour accepter les connexions
    private TextArea zoneJournal; // Zone pour afficher les logs
    private ListView<String> vueListeClients; // Liste pour afficher les clients connectés
    private Button boutonDemarrer, boutonArreter; // Boutons pour contrôler le serveur
    private PrintWriter ecrivainFichierJournal; // Écrivain pour sauvegarder les logs dans un fichier
    private final HashMap<String, String> utilisateurs = new HashMap<>(); // Base de données simple des utilisateurs

    // Constructeur : initialise les identifiants par défaut
    public ServeurControleDistance() {
        utilisateurs.put("admin", "password123");
    }

    public static void main(String[] args) {
        launch(args); // Lancement de l'application JavaFX
    }

    @Override
    public void start(Stage fenetrePrincipale) {
        fenetrePrincipale.setTitle("Serveur");

        // Initialisation du fichier journal
        try {
            ecrivainFichierJournal = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream("journal_serveur.txt", true), StandardCharsets.UTF_8),
                    true);
        } catch (IOException e) {
            System.err.println("Erreur création fichier journal : " + e.getMessage());
        }

        // Configuration de l'interface graphique
        zoneJournal = new TextArea();
        zoneJournal.setEditable(false);
        zoneJournal.setPrefHeight(300);
        zoneJournal.setPromptText("Journal des événements...");

        vueListeClients = new ListView<>();
        vueListeClients.setPrefHeight(100);
        vueListeClients.setPlaceholder(new Label("Aucun client connecté"));

        boutonDemarrer = new Button("Démarrer le Serveur");
        boutonArreter = new Button("Arrêter le Serveur");
        boutonArreter.setDisable(true);

        boutonDemarrer.setOnAction(e -> demarrerServeur());
        boutonArreter.setOnAction(e -> arreterServeur());

        VBox miseEnPage = new VBox(10, new Label("Clients connectés:"), vueListeClients, new Label("Journal:"),
                zoneJournal, boutonDemarrer, boutonArreter);
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

    // Démarre le serveur dans un thread séparé
    private void demarrerServeur() {
        if (estActif) return;
        estActif = true;
        boutonDemarrer.setDisable(true);
        boutonArreter.setDisable(false);

        new Thread(() -> {
            try {
                // Configuration SSL pour le serveur
                System.setProperty("javax.net.ssl.keyStore", "server.keystore");
                System.setProperty("javax.net.ssl.keyStorePassword", "changeit");

                SSLServerSocketFactory fabrique = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
                socketServeur = (SSLServerSocket) fabrique.createServerSocket(1234);
                journaliser("Serveur SSL démarré sur le port 1234...");

                // Boucle pour accepter les connexions des clients
                while (estActif) {
                    SSLSocket socketClient = (SSLSocket) socketServeur.accept();
                    GestionnaireClient gestionnaireClient = new GestionnaireClient(socketClient);
                    synchronized (listeClients) {
                        listeClients.add(gestionnaireClient);
                    }
                    Platform.runLater(
                            () -> vueListeClients.getItems().add(socketClient.getRemoteSocketAddress().toString()));
                    gestionnaireClient.start();
                }
            } catch (IOException e) {
                journaliser("Erreur démarrage serveur : " + e.getMessage());
            }
        }).start();
    }

    // Arrête le serveur et ferme toutes les connexions
    private void arreterServeur() {
        if (!estActif) return;
        estActif = false;
        boutonDemarrer.setDisable(false);
        boutonArreter.setDisable(true);

        synchronized (listeClients) {
            for (GestionnaireClient client : listeClients) {
                client.fermerConnexion();
            }
            listeClients.clear();
        }
        Platform.runLater(() -> vueListeClients.getItems().clear());

        try {
            if (socketServeur != null && !socketServeur.isClosed()) socketServeur.close();
            journaliser("Serveur arrêté.");
        } catch (IOException e) {
            journaliser("Erreur arrêt serveur : " + e.getMessage());
        }
        if (ecrivainFichierJournal != null) ecrivainFichierJournal.close();
    }

    // Ajoute un message au journal (interface et fichier)
    private void journaliser(String message) {
        String messageFormate = String.format("[%tT] %s", new java.util.Date(), message);
        Platform.runLater(() -> zoneJournal.appendText(messageFormate + "\n"));
        if (ecrivainFichierJournal != null) ecrivainFichierJournal.println(messageFormate);
    }

    // Classe interne pour gérer chaque client dans un thread séparé
    class GestionnaireClient extends Thread {
        private final SSLSocket socket;
        private BufferedReader entree; // Flux d'entrée pour lire les données du client
        private PrintWriter sortie; // Flux de sortie pour envoyer des données au client
        private boolean estAuthentifie = false; // Indicateur d'authentification

        public GestionnaireClient(SSLSocket socket) {
            this.socket = socket;
            try {
                entree = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                sortie = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                        true);
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

                    // Boucle pour traiter les commandes du client
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

        // Vérifie les identifiants du client
        private boolean authentifier(String identifiants) {
            String[] parties = identifiants.split(":");
            if (parties.length != 2) return false;
            String login = parties[0];
            String motDePasse = parties[1];
            return utilisateurs.containsKey(login) && utilisateurs.get(login).equals(motDePasse);
        }

        // Exécute une commande reçue du client
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
                    // Adaptation selon le système d'exploitation
                    if (System.getProperty("os.name").toLowerCase().contains("win")) {
                        pb = new ProcessBuilder("cmd.exe", "/c", commande);
                    } else {
                        pb = new ProcessBuilder("/bin/sh", "-c", commande);
                    }
                    pb.redirectErrorStream(true);
                    Process processus = pb.start();

                    // Capture de la sortie de la commande
                    BufferedReader lecteur = new BufferedReader(
                            new InputStreamReader(processus.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder resultat = new StringBuilder();
                    String ligne;
                    while ((ligne = lecteur.readLine()) != null) {
                        resultat.append(ligne).append("\n");
                    }
                    processus.waitFor();

                    sortie.println("RESULT:" + resultat.toString());
                } catch (Exception e) {
                    sortie.println("ERROR:Erreur lors de l'exécution : " + e.getMessage());
                }
            }
        }

        // Reçoit un fichier envoyé par le client
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

        // Envoie un fichier au client
        private void envoyerFichier(String nomFichier) {
            try {
                File fichier = new File("server_files/" + nomFichier);
                if (!fichier.exists()) {
                    sortie.println("ERROR:Fichier introuvable");
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
            } catch (IOException e) {
                sortie.println("ERROR:Erreur download : " + e.getMessage());
                journaliser("Erreur envoi fichier : " + e.getMessage());
            }
        }

        // Ferme la connexion avec le client
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