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

public class ClientControleDistance extends Application {
    // Variables pour la gestion de la connexion réseau SSL
    private SSLSocket socket; // Socket sécurisé pour la communication avec le serveur
    private PrintWriter sortie; // Flux de sortie pour envoyer des données au serveur
    private BufferedReader entree; // Flux d'entrée pour recevoir des données du serveur
    private boolean estConnecte = false; // Indicateur de l'état de la connexion
    private Thread filReception; // Thread pour recevoir les réponses du serveur en continu

    // Composants de l'interface graphique
    private Button boutonConnexion, boutonDeconnexion, boutonEnvoyer, boutonUploader, boutonTelecharger;
    private TextField champCommande, champHote, champPort, champLogin;
    private PasswordField champMotDePasse;
    private ListView<String> vueListeResultats; // Liste pour afficher les résultats
    private ObservableList<String> listeResultats; // Liste observable des résultats
    private ObservableList<String> historiqueCommandes = FXCollections.observableArrayList(); // Historique des commandes
    private Label etiquetteStatut; // Étiquette pour afficher l'état de la connexion

    public static void main(String[] args) {
        launch(args); // Lancement de l'application JavaFX
    }

    @Override
    public void start(Stage fenetrePrincipale) {
        // Configuration de la fenêtre principale
        fenetrePrincipale.setTitle("Client de Contrôle à Distance - ESP Dakar");
        BorderPane racine = new BorderPane();

        // Section connexion : champs et boutons pour établir la connexion
        champHote = new TextField("localhost"); // Champ pour l'adresse IP ou le nom d'hôte
        champPort = new TextField("1234"); // Champ pour le port du serveur
        champLogin = new TextField("admin"); // Champ pour le login
        champMotDePasse = new PasswordField(); // Champ pour le mot de passe
        champMotDePasse.setText("password123");
        boutonConnexion = new Button("Se connecter");
        boutonDeconnexion = new Button("Se déconnecter");
        boutonDeconnexion.setDisable(true); // Désactivé par défaut

        HBox boiteConnexion = new HBox(10, new Label("Hôte:"), champHote, new Label("Port:"), champPort,
                new Label("Login:"), champLogin, new Label("Mot de passe:"), champMotDePasse,
                boutonConnexion, boutonDeconnexion);
        boiteConnexion.setPadding(new Insets(10));
        boiteConnexion.setBackground(new Background(new BackgroundFill(Color.LIGHTGRAY, null, null)));
        racine.setTop(boiteConnexion);

        // Section résultats : affichage des réponses du serveur
        listeResultats = FXCollections.observableArrayList();
        vueListeResultats = new ListView<>(listeResultats);
        vueListeResultats.setPrefHeight(300);
        VBox boiteResultats = new VBox(10, new Label("Résultats:"), vueListeResultats);
        boiteResultats.setPadding(new Insets(10));
        racine.setCenter(boiteResultats);

        // Section commande : saisie et envoi des commandes
        champCommande = new TextField();
        champCommande.setPrefWidth(400);
        champCommande.setDisable(true); // Désactivé tant que non connecté
        boutonEnvoyer = new Button("Envoyer");
        boutonEnvoyer.setDisable(true);
        boutonUploader = new Button("Uploader Fichier");
        boutonUploader.setDisable(true);
        boutonTelecharger = new Button("Télécharger Fichier");
        boutonTelecharger.setDisable(true);

        // Historique des commandes avec possibilité de réutilisation par double-clic
        ListView<String> vueHistoriqueCommandes = new ListView<>(historiqueCommandes);
        vueHistoriqueCommandes.setPrefHeight(100);
        vueHistoriqueCommandes.setPlaceholder(new Label("Aucune commande dans l'historique"));
        vueHistoriqueCommandes.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String selection = vueHistoriqueCommandes.getSelectionModel().getSelectedItem();
                if (selection != null) champCommande.setText(selection);
            }
        });

        etiquetteStatut = new Label("Statut : Déconnecté");
        etiquetteStatut.setTextFill(Color.RED);

        HBox boiteCommande = new HBox(10, new Label("Commande:"), champCommande, boutonEnvoyer, boutonUploader, boutonTelecharger);
        boiteCommande.setPadding(new Insets(10));
        VBox boiteBas = new VBox(10, boiteCommande, new Label("Historique des commandes:"), vueHistoriqueCommandes, etiquetteStatut);
        boiteBas.setPadding(new Insets(10));
        racine.setBottom(boiteBas);

        // Configuration et affichage de la scène
        Scene scene = new Scene(racine, 800, 600);
        fenetrePrincipale.setScene(scene);
        fenetrePrincipale.show();

        // Gestion des événements
        boutonConnexion.setOnAction(e -> connecterAuServeur());
        boutonDeconnexion.setOnAction(e -> deconnecterDuServeur());
        boutonEnvoyer.setOnAction(e -> envoyerCommande());
        boutonUploader.setOnAction(e -> uploaderFichier(fenetrePrincipale));
        boutonTelecharger.setOnAction(e -> telechargerFichier());
        champCommande.setOnAction(e -> envoyerCommande());

        // Fermeture propre de l'application
        fenetrePrincipale.setOnCloseRequest(e -> {
            deconnecterDuServeur();
            Platform.exit();
        });
    }

    // Établit une connexion sécurisée avec le serveur
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
            // Configuration SSL pour le client
            System.setProperty("javax.net.ssl.trustStore", "client.keystore");
            System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
    
            SSLSocketFactory fabrique = (SSLSocketFactory) SSLSocketFactory.getDefault();
            socket = (SSLSocket) fabrique.createSocket(hote, port);
            sortie = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            entree = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            estConnecte = true;
    
            // Envoi des identifiants au serveur
            String identifiants = champLogin.getText().trim() + ":" + champMotDePasse.getText().trim();
            sortie.println(identifiants);
    
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
    
    // Ferme la connexion avec le serveur
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

        mettreAJourInterfaceApresConnexion(false);
        listeResultats.add("🔴 Déconnecté du serveur");
        etiquetteStatut.setText("Statut : Déconnecté");
        etiquetteStatut.setTextFill(Color.RED);
    }

    // Envoie une commande au serveur
    private void envoyerCommande() {
        if (!estConnecte) return;
        String commande = champCommande.getText().trim();
        if (commande.isEmpty()) return;

        listeResultats.add("> " + commande);
        historiqueCommandes.add(commande);
        sortie.println(commande);
        champCommande.clear();
    }

    // Permet d'uploader un fichier vers le serveur
    private void uploaderFichier(Stage fenetre) {
        if (!estConnecte) return;

        FileChooser selecteurFichier = new FileChooser();
        selecteurFichier.setTitle("Choisir un fichier à uploader");
        File fichier = selecteurFichier.showOpenDialog(fenetre);
        if (fichier == null) return;

        try {
            sortie.println("UPLOAD:" + fichier.getName());
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

    // Télécharge un fichier depuis le serveur et le stocke localement
    private void telechargerFichier() {
        if (!estConnecte) return;
        String nomFichier = champCommande.getText().trim();
        if (nomFichier.isEmpty()) {
            listeResultats.add("Erreur : Nom de fichier vide");
            return;
        }

        sortie.println("DOWNLOAD:" + nomFichier);

        // Création du dossier local "client_files" s'il n'existe pas
        File dossier = new File("client_files");
        if (!dossier.exists()) dossier.mkdir();

        try {
            FileOutputStream fluxSortieFichier = new FileOutputStream("client_files/" + nomFichier);
            InputStream fluxEntree = socket.getInputStream();
            byte[] tampon = new byte[1024];
            int octetsLus;
            while ((octetsLus = fluxEntree.read(tampon)) != -1) {
                fluxSortieFichier.write(tampon, 0, octetsLus);
                if (octetsLus < tampon.length) break; // Fin du fichier
            }
            fluxSortieFichier.close();
            listeResultats.add("Fichier téléchargé et stocké : " + nomFichier);
        } catch (IOException e) {
            listeResultats.add("Erreur téléchargement : " + e.getMessage());
        }
    }

    // Reçoit les réponses du serveur en continu
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

    // Met à jour l'état des éléments de l'interface selon la connexion
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