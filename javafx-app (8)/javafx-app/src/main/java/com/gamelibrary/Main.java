package com.gamelibrary;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Main extends Application {

    private static final int COLUMNS = 4;

    private enum Filter { ALL, RECENT, FAVORITES }

    private final ObservableList<Game> games = FXCollections.observableArrayList();
    private Game selectedGame;
    private Filter activeFilter = Filter.ALL;
    private GameDao gameDao;

    private GridPane grid;
    private Label statusLabel;
    private Button detailsButton;
    private Button editButton;
    private Button deleteButton;
    private ToggleButton allTab;
    private ToggleButton recentTab;
    private ToggleButton favoritesTab;
    private Label clockLabel;

    @Override
    public void start(Stage stage) {
        try {
            gameDao = new GameDao();
            loadInitialData();
        } catch (Exception e) {
            showFatalErrorAndExit(
                    "Could not start the application",
                    "The game library database could not be opened or initialized.\n\n"
                            + "Details: " + e.getMessage()
            );
            return;
        }

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(buildTopBar());
        root.setCenter(buildGridArea());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1452, 747);
        java.net.URL cssUrl = getClass().getResource("/com/gamelibrary/style.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println("Warning: style.css not found on the classpath; running with default styling.");
        }

        stage.setTitle("My Game Library");
        stage.setScene(scene);
        stage.show();

        startClock();
        refreshGrid();
    }

    private void showFatalErrorAndExit(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Fatal Error");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
        javafx.application.Platform.exit();
    }

    private void showErrorAlert(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        if (gameDao != null) {
            gameDao.close();
        }
    }

    // ---------- Data loading ----------

    private void loadInitialData() {
        List<Game> loaded = gameDao.loadAll();
        if (loaded.isEmpty()) {
            // First run ever — seed one sample game and persist it.
            Game starter = new Game("Elden Ring", 147, false, LocalDateTime.now().minusHours(2),
                    "Action RPG", "PlayStation 5");
            gameDao.insert(starter);
            loaded.add(starter);
        }
        games.setAll(loaded);
    }

    // ---------- Top bar ----------

    private HBox buildTopBar() {
        HBox bar = new HBox();
        bar.getStyleClass().add("top-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(12, 20, 12, 20));
        bar.setSpacing(14);

        Label logo = new Label("\u2637");
        logo.getStyleClass().add("logo-badge");

        Label title = new Label("My Game Library");
        title.getStyleClass().add("app-title");

        allTab = new ToggleButton("All");
        recentTab = new ToggleButton("Recent");
        favoritesTab = new ToggleButton("Favorites");
        ToggleGroup filterGroup = new ToggleGroup();
        for (ToggleButton tab : List.of(allTab, recentTab, favoritesTab)) {
            tab.getStyleClass().add("filter-tab");
            tab.setToggleGroup(filterGroup);
        }
        allTab.setSelected(true);
        allTab.setOnAction(e -> setFilter(Filter.ALL));
        recentTab.setOnAction(e -> setFilter(Filter.RECENT));
        favoritesTab.setOnAction(e -> setFilter(Filter.FAVORITES));

        HBox tabs = new HBox(8, allTab, recentTab, favoritesTab);
        tabs.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addButton = new Button("+  Add Game");
        addButton.getStyleClass().addAll("pill-button", "add-button");
        addButton.setOnAction(e -> onAddGame());

        detailsButton = new Button("\uD83D\uDC41  Details");
        detailsButton.getStyleClass().add("pill-button");
        detailsButton.setOnAction(e -> onShowDetails());

        editButton = new Button("\u270E  Edit");
        editButton.getStyleClass().add("pill-button");
        editButton.setOnAction(e -> onEditGame());

        deleteButton = new Button("\uD83D\uDDD1  Delete");
        deleteButton.getStyleClass().addAll("pill-button", "delete-button");
        deleteButton.setOnAction(e -> onDeleteGame());

        detailsButton.setDisable(true);
        editButton.setDisable(true);
        deleteButton.setDisable(true);

        clockLabel = new Label();
        clockLabel.getStyleClass().add("clock-label");

        bar.getChildren().addAll(logo, title, tabs, spacer,
                addButton, detailsButton, editButton, deleteButton, clockLabel);
        return bar;
    }

    private void setFilter(Filter filter) {
        this.activeFilter = filter;
        refreshGrid();
    }

    // ---------- Grid area ----------

    private ScrollPane buildGridArea() {
        grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(20);
        grid.setPadding(new Insets(30));

        ScrollPane scroll = new ScrollPane(grid);
        scroll.getStyleClass().add("grid-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private List<Game> filteredGames() {
        List<Game> list = new ArrayList<>(games);
        switch (activeFilter) {
            case FAVORITES:
                list.removeIf(g -> !g.isFavorite());
                break;
            case RECENT:
                list.sort(Comparator.comparing(Game::getLastPlayed,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed());
                if (list.size() > 5) {
                    list = list.subList(0, 5);
                }
                break;
            case ALL:
            default:
                break;
        }
        return list;
    }

    private void refreshGrid() {
        grid.getChildren().clear();
        List<Game> visible = filteredGames();

        int totalSlots = ((visible.size() / COLUMNS) + 1) * COLUMNS;
        if (visible.size() % COLUMNS == 0) {
            totalSlots = visible.size() + COLUMNS;
        }

        int col = 0;
        int row = 0;
        for (Game game : visible) {
            grid.add(buildFilledTile(game), col, row);
            col++;
            if (col == COLUMNS) {
                col = 0;
                row++;
            }
        }
        for (int i = visible.size(); i < totalSlots; i++) {
            grid.add(buildEmptyTile(), col, row);
            col++;
            if (col == COLUMNS) {
                col = 0;
                row++;
            }
        }
    }

    private StackPane buildFilledTile(Game game) {
        StackPane tile = new StackPane();
        tile.getStyleClass().add("filled-tile");
        if (game == selectedGame) {
            tile.getStyleClass().add("tile-selected");
        }
        tile.setPrefSize(150, 150);

        VBox content = new VBox(8);
        content.setAlignment(Pos.CENTER);

        Label icon = new Label(game.getInitial());
        icon.getStyleClass().add("tile-icon");

        Label name = new Label(game.getName());
        name.getStyleClass().add("tile-name");
        name.setWrapText(true);
        name.setAlignment(Pos.CENTER);

        Label hours = new Label(game.getHoursLabel());
        hours.getStyleClass().add("tile-hours");

        content.getChildren().addAll(icon, name, hours);
        tile.getChildren().add(content);

        tile.setOnMouseClicked(e -> selectGame(game));
        return tile;
    }

    private StackPane buildEmptyTile() {
        StackPane tile = new StackPane();
        tile.getStyleClass().add("empty-tile");
        tile.setPrefSize(150, 150);

        Label plus = new Label("+");
        plus.getStyleClass().add("empty-tile-plus");
        tile.getChildren().add(plus);

        tile.setOnMouseClicked(e -> onAddGame());
        return tile;
    }

    private void selectGame(Game game) {
        this.selectedGame = game;
        detailsButton.setDisable(false);
        editButton.setDisable(false);
        deleteButton.setDisable(false);
        StringBuilder status = new StringBuilder(game.getName());
        status.append("  \u2022  ").append(game.getHoursLabel()).append(" played");
        status.append("  \u2022  ").append(game.getCompletionLabel()).append(" complete");
        if (game.getGenre() != null && !game.getGenre().isBlank()) {
            status.append("  \u2022  ").append(game.getGenre());
        }
        if (game.getConsole() != null && !game.getConsole().isBlank()) {
            status.append("  \u2022  ").append(game.getConsole());
        }
        if (game.isFavorite()) {
            status.append("  \u2022  \u2605 Favorite");
        }
        statusLabel.setText(status.toString());
        refreshGrid();
    }

    private void clearSelection() {
        this.selectedGame = null;
        detailsButton.setDisable(true);
        editButton.setDisable(true);
        deleteButton.setDisable(true);
        statusLabel.setText("Select a tile to view details");
    }

    // ---------- Status bar ----------

    private HBox buildStatusBar() {
        HBox bar = new HBox();
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(16));

        statusLabel = new Label("Select a tile to view details");
        statusLabel.getStyleClass().add("status-label");

        bar.getChildren().add(statusLabel);
        return bar;
    }

    // ---------- Clock ----------

    private void startClock() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a");
        clockLabel.setText(LocalDateTime.now().format(formatter));
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e ->
                clockLabel.setText(LocalDateTime.now().format(formatter))));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    // ---------- Actions ----------

    private void onAddGame() {
        Optional<Game> result = GameDialog.show(null, games);
        result.ifPresent(newGame -> {
            // Defensive backstop: the dialog's live validation should already have
            // blocked this, but double-check here too in case anything changed
            // between the dialog closing and this point.
            if (isDuplicate(newGame, null)) {
                showErrorAlert("Duplicate game",
                        "\"" + newGame.getName() + "\" already exists in your library for that console.");
                return;
            }
            try {
                gameDao.insert(newGame);
                games.add(newGame);
                refreshGrid();
            } catch (Exception e) {
                showErrorAlert("Could not add game",
                        "\"" + newGame.getName() + "\" could not be saved to the database.\n\n"
                                + "Details: " + e.getMessage());
            }
        });
    }

    /** True if another game in the library already shares this name + console (case-insensitive). */
    private boolean isDuplicate(Game candidate, Game excluding) {
        String name = candidate.getName();
        String console = candidate.getConsole() == null ? "" : candidate.getConsole();
        for (Game other : games) {
            if (other == excluding) {
                continue;
            }
            boolean sameName = other.getName() != null && other.getName().equalsIgnoreCase(name);
            String otherConsole = other.getConsole() == null ? "" : other.getConsole();
            boolean sameConsole = otherConsole.equalsIgnoreCase(console);
            if (sameName && sameConsole) {
                return true;
            }
        }
        return false;
    }

    private void onEditGame() {
        if (selectedGame == null) return;

        // Snapshot current values so we can roll back if the database write fails,
        // since the dialog edits the Game object in place.
        String prevName = selectedGame.getName();
        double prevHours = selectedGame.getHoursPlayed();
        double prevPercent = selectedGame.getCompletionPercent();
        boolean prevFavorite = selectedGame.isFavorite();
        String prevGenre = selectedGame.getGenre();
        String prevConsole = selectedGame.getConsole();

        Optional<Game> result = GameDialog.show(selectedGame, games);
        result.ifPresent(updated -> {
            if (isDuplicate(updated, selectedGame)) {
                // Roll back before reporting, since the dialog already mutated the object in place.
                updated.setName(prevName);
                updated.setHoursPlayed(prevHours);
                updated.setCompletionPercent(prevPercent);
                updated.setFavorite(prevFavorite);
                updated.setGenre(prevGenre);
                updated.setConsole(prevConsole);
                refreshGrid();
                showErrorAlert("Duplicate game",
                        "Another game with that name and console already exists in your library.");
                return;
            }
            try {
                gameDao.update(updated);
                selectGame(updated);
                refreshGrid();
            } catch (Exception e) {
                // Roll back the in-memory object to match what's actually in the database.
                updated.setName(prevName);
                updated.setHoursPlayed(prevHours);
                updated.setCompletionPercent(prevPercent);
                updated.setFavorite(prevFavorite);
                updated.setGenre(prevGenre);
                updated.setConsole(prevConsole);
                refreshGrid();
                showErrorAlert("Could not save changes",
                        "Your edits to \"" + prevName + "\" could not be saved, so they were reverted.\n\n"
                                + "Details: " + e.getMessage());
            }
        });
    }

    private void onDeleteGame() {
        if (selectedGame == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Game");
        confirm.setHeaderText("Delete \"" + selectedGame.getName() + "\"?");
        confirm.setContentText("This action cannot be undone.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                gameDao.delete(selectedGame);
                games.remove(selectedGame);
                clearSelection();
                refreshGrid();
            } catch (Exception e) {
                showErrorAlert("Could not delete game",
                        "\"" + selectedGame.getName() + "\" could not be removed from the database.\n\n"
                                + "Details: " + e.getMessage());
            }
        }
    }

    private void onShowDetails() {
        if (selectedGame == null) return;
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Game Details");
        info.setHeaderText(selectedGame.getName());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' hh:mm a");
        String genre = selectedGame.getGenre() == null || selectedGame.getGenre().isBlank()
                ? "Not set" : selectedGame.getGenre();
        String console = selectedGame.getConsole() == null || selectedGame.getConsole().isBlank()
                ? "Not set" : selectedGame.getConsole();
        String content = "Genre: " + genre + "\n"
                + "Console: " + console + "\n"
                + "Hours played: " + selectedGame.getHoursLabel() + "\n"
                + "Completion: " + selectedGame.getCompletionLabel() + "\n"
                + "Favorite: " + (selectedGame.isFavorite() ? "Yes" : "No") + "\n"
                + "Last played: " + selectedGame.getLastPlayed().format(fmt);
        info.setContentText(content);
        info.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
