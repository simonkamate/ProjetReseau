import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javax.net.ssl.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

// Classe principale du client de contrôle à distance
public class ClientControleDistance extends Application {
    // Socket SSL pour la connexion au serveur
    private SSLSocket socket;
    // Flux de sortie pour envoyer des données au serveur
    private PrintWriter sortie;
    // Flux d'entrée pour recevoir des données du serveur
    private BufferedReader entree;
    // Indicateur d'état de la connexion
    private boolean estConnecte = false;
    // Thread pour recevoir les messages du serveur
    private Thread filReception;

    // Boutons de l'interface graphique
    private Button boutonConnexion, boutonDeconnexion, boutonEnvoyer, boutonUploader, boutonTelecharger;
    // Champs de texte pour la saisie
    private TextField champCommande, champHote, champPort, champLogin;
    // Champ pour le mot de passe
    private PasswordField champMotDePasse;
    // Liste pour afficher les résultats
    private ListView<String> vueListeResultats;
    // Liste observable pour stocker les résultats
    private ObservableList<String> listeResultats;
    // Liste observable pour l'historique des commandes
    private ObservableList<String> historiqueCommandes = FXCollections.observableArrayList();
    // Étiquette pour afficher l'état de la connexion
    private Label etiquetteStatut;

    // Point d'entrée de l'application
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage fenetrePrincipale) {
        // Définit le titre de la fenêtre principale
        fenetrePrincipale.setTitle("Client de Contrôle à Distance - ESP Dakar");

        // Mise en page principale
        BorderPane racine = new BorderPane();

        // Configuration des champs de connexion
        champHote = new TextField("localhost");
        champPort = new TextField("1234");
        champLogin = new TextField("admin");
        champMotDePasse = new PasswordField();
        champMotDePasse.setText("password123");
        boutonConnexion = new Button("Se connecter");
        boutonDeconnexion = new Button("Se déconnecter");
        boutonDeconnexion.setDisable(true);

        // Mise en page de la section de connexion
        HBox boiteConnexion = new HBox(10, new Label("Hôte:"), champHote, new Label("Port:"), champPort,
                new Label("Login:"), champLogin, new Label("Mot de passe:"), champMotDePasse,
                boutonConnexion, boutonDeconnexion);
        boiteConnexion.setPadding(new Insets(10));
        boiteConnexion.setBackground(new Background(new BackgroundFill(Color.LIGHTGRAY, null, null)));
        racine.setTop(boiteConnexion);

        // Configuration de la liste des résultats
        listeResultats = FXCollections.observableArrayList();
        vueListeResultats = new ListView<>(listeResultats);
        vueListeResultats.setPrefHeight(300);
        VBox boiteResultats = new VBox(10, new Label("Résultats:"), vueListeResultats);
        boiteResultats.setPadding(new Insets(10));
        racine.setCenter(boiteResultats);

        // Configuration du champ de commande et des boutons associés
        champCommande = new TextField();
        champCommande.setPrefWidth(400);
        champCommande.setDisable(true);
        boutonEnvoyer = new Button("Envoyer");
        boutonEnvoyer.setDisable(true);
        boutonUploader = new Button("Uploader Fichier");
        boutonUploader.setDisable(true);
        boutonTelecharger = new Button("Télécharger Fichier");
        boutonTelecharger.setDisable(true);

        // Configuration de la liste de l'historique des commandes
        ListView<String> vueHistoriqueCommandes = new ListView<>(historiqueCommandes);
        vueHistoriqueCommandes.setPrefHeight(100);
        vueHistoriqueCommandes.setPlaceholder(new Label("Aucune commande dans l'historique"));
        vueHistoriqueCommandes.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String selection = vueHistoriqueCommandes.getSelectionModel().getSelectedItem();
                if (selection != null) champCommande.setText(selection);
            }
        });

        // Configuration de l'étiquette de statut
        etiquetteStatut = new Label("Statut : Déconnecté");
        etiquetteStatut.setTextFill(Color.RED);

        // Mise en page de la section des commandes
        HBox boiteCommande = new HBox(10, new Label("Commande:"), champCommande, boutonEnvoyer, boutonUploader, boutonTelecharger);
        boiteCommande.setPadding(new Insets(10));
        VBox boiteBas = new VBox(10, boiteCommande, new Label("Historique des commandes:"), vueHistoriqueCommandes, etiquetteStatut);
        boiteBas.setPadding(new Insets(10));
        racine.setBottom(boiteBas);

        // Configuration de la scène
        Scene scene = new Scene(racine, 800, 600);
        fenetrePrincipale.setScene(scene);
        fenetrePrincipale.show();

        // Actions des boutons
        boutonConnexion.setOnAction(e -> connecterAuServeur());
        boutonDeconnexion.setOnAction(e -> deconnecterDuServeur());
        boutonEnvoyer.setOnAction(e -> envoyerCommande());
        boutonUploader.setOnAction(e -> uploaderFichier(fenetrePrincipale));
        boutonTelecharger.setOnAction(e -> telechargerFichier());
        champCommande.setOnAction(e -> envoyerCommande());

        // Gestion de la fermeture de la fenêtre
        fenetrePrincipale.setOnCloseRequest(e -> {
            deconnecterDuServeur();
            Platform.exit();
        });
    }

    // Méthode pour connecter le client au serveur
    private void connecterAuServeur() {
        if (estConnecte) return;
        String hote = champHote.getText().trim();
        int port;
        try {
            port = Integer.parseInt(champPort.getText().trim());
        } catch (NumberFormatException e) {
            listeResultats.add("Erreur : Port invalide");
            return;
        }

        try {
            // Configuration du truststore pour SSL
            System.setProperty("javax.net.ssl.trustStore", "server.keystore");
            System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
            SSLSocketFactory fabrique = (SSLSocketFactory) SSLSocketFactory.getDefault();
            socket = (SSLSocket) fabrique.createSocket(hote, port);
            // Utilisation de l'encodage UTF-8 pour les flux
            sortie = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            entree = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            estConnecte = true;

            // Envoi des identifiants pour l'authentification
            String identifiants = champLogin.getText().trim() + ":" + champMotDePasse.getText().trim();
            sortie.println(identifiants);
            sortie.flush();

            // Mise à jour de l'interface après connexion
            mettreAJourInterfaceApresConnexion(true);
            listeResultats.add("✅ Connecté au serveur " + hote + ":" + port);
            etiquetteStatut.setText("Statut : Connecté");
            etiquetteStatut.setTextFill(Color.GREEN);

            // Lancement du thread de réception
            filReception = new Thread(this::recevoirDuServeur);
            filReception.start();
        } catch (IOException e) {
            listeResultats.add("Erreur de connexion : " + e.getMessage());
        }
    }

    // Méthode pour déconnecter le client du serveur
    private void deconnecterDuServeur() {
        if (!estConnecte) return;
        estConnecte = false;

        try {
            if (filReception != null && filReception.isAlive()) filReception.interrupt();
            if (sortie != null) sortie.close();
            if (entree != null) entree.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            listeResultats.add("Erreur déconnexion : " + e.getMessage());
        }

        // Mise à jour de l'interface après déconnexion
        mettreAJourInterfaceApresConnexion(false);
        listeResultats.add("🔴 Déconnecté du serveur");
        etiquetteStatut.setText("Statut : Déconnecté");
        etiquetteStatut.setTextFill(Color.RED);
    }

    // Méthode pour envoyer une commande au serveur
    private void envoyerCommande() {
        if (!estConnecte) return;
        String commande = champCommande.getText().trim();
        if (commande.isEmpty()) return;

        listeResultats.add("> " + commande);
        historiqueCommandes.add(commande);
        sortie.println(commande);
        sortie.flush();
        champCommande.clear();
    }

    // Méthode pour uploader un fichier vers le serveur
    private void uploaderFichier(Stage fenetre) {
        if (!estConnecte) return;

        // Ouvre un sélecteur de fichier
        FileChooser selecteurFichier = new FileChooser();
        selecteurFichier.setTitle("Choisir un fichier à uploader");
        File fichier = selecteurFichier.showOpenDialog(fenetre);
        if (fichier == null) {
            listeResultats.add("Aucun fichier sélectionné");
            return;
        }

        // Met à jour le champ de commande avec le nom du fichier
        champCommande.setText(fichier.getName());
        sortie.println("UPLOAD:" + fichier.getName());
        try {
            FileInputStream fluxEntreeFichier = new FileInputStream(fichier);
            OutputStream fluxSortie = socket.getOutputStream();
            byte[] tampon = new byte[1024];
            int octetsLus;
            while ((octetsLus = fluxEntreeFichier.read(tampon)) != -1) {
                fluxSortie.write(tampon, 0, octetsLus);
            }
            fluxEntreeFichier.close();
            fluxSortie.flush();
            listeResultats.add("Fichier uploadé : " + fichier.getName());
        } catch (IOException e) {
            listeResultats.add("Erreur upload : " + e.getMessage());
        }
    }

    // Méthode pour télécharger un fichier depuis le serveur
    private void telechargerFichier() {
        if (!estConnecte) return;
        String nomFichier = champCommande.getText().trim();
        if (nomFichier.isEmpty()) {
            listeResultats.add("Erreur : Nom de fichier vide");
            return;
        }

        sortie.println("DOWNLOAD:" + nomFichier);
        try {
            FileOutputStream fluxSortieFichier = new FileOutputStream("downloaded_" + nomFichier);
            InputStream fluxEntree = socket.getInputStream();
            byte[] tampon = new byte[1024];
            int octetsLus;
            while ((octetsLus = fluxEntree.read(tampon)) != -1) {
                fluxSortieFichier.write(tampon, 0, octetsLus);
                if (octetsLus < tampon.length) break;
            }
            fluxSortieFichier.close();
            listeResultats.add("Fichier téléchargé : " + nomFichier);
        } catch (IOException e) {
            listeResultats.add("Erreur download : " + e.getMessage());
        }
    }

    // Méthode pour recevoir les messages du serveur
    private void recevoirDuServeur() {
        try {
            String reponse;
            while ((reponse = entree.readLine()) != null && estConnecte) {
                String reponseFinale = reponse;
                Platform.runLater(() -> {
                    if (reponseFinale.startsWith("RESULT:")) {
                        listeResultats.add(reponseFinale.substring(7));
                    } else if (reponseFinale.startsWith("ERROR:")) {
                        listeResultats.add("⚠ " + reponseFinale.substring(6));
                    } else {
                        listeResultats.add(reponseFinale);
                    }
                });
            }
        } catch (IOException e) {
            if (estConnecte) {
                Platform.runLater(() -> {
                    listeResultats.add("❌ Erreur de communication : " + e.getMessage());
                    deconnecterDuServeur();
                });
            }
        }
    }

    // Méthode pour mettre à jour l'interface après connexion/déconnexion
    private void mettreAJourInterfaceApresConnexion(boolean estConnecte) {
        Platform.runLater(() -> {
            boutonConnexion.setDisable(estConnecte);
            boutonDeconnexion.setDisable(!estConnecte);
            champCommande.setDisable(!estConnecte);
            boutonEnvoyer.setDisable(!estConnecte);
            boutonUploader.setDisable(!estConnecte);
            boutonTelecharger.setDisable(!estConnecte);
        });
    }
}