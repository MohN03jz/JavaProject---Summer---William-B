package com.gamelibrary;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Modal dialog used for both "Add Game" and "Edit Game".
 * Pass an existing Game to pre-fill the fields for editing,
 * or null to create a brand new one.
 */
public final class GameDialog {

    private GameDialog() {
    }

    /**
     * Shows the dialog and returns the resulting Game, or empty if the
     * user cancelled.
     *
     * @param existing the game to edit, or null to create a new one
     * @param allGames the full current library, used to detect duplicate entries
     */
    public static Optional<Game> show(Game existing, List<Game> allGames) {
        Dialog<Game> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Game" : "Edit Game");
        dialog.setHeaderText(existing == null
                ? "Add a new game to your library"
                : "Edit \"" + existing.getName() + "\"");

        ButtonType saveButtonType = new ButtonType(existing == null ? "Add" : "Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("Game title");

        Label nameErrorLabel = new Label();
        nameErrorLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 11px;");
        nameErrorLabel.setVisible(false);
        nameErrorLabel.setManaged(false);

        Label duplicateErrorLabel = new Label();
        duplicateErrorLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 11px;");
        duplicateErrorLabel.setVisible(false);
        duplicateErrorLabel.setManaged(false);

        TextField hoursField = new TextField();
        hoursField.setPromptText("0");

        Label hoursErrorLabel = new Label();
        hoursErrorLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 11px;");
        hoursErrorLabel.setVisible(false);
        hoursErrorLabel.setManaged(false);

        TextField percentField = new TextField();
        percentField.setPromptText("0");

        Label percentErrorLabel = new Label();
        percentErrorLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 11px;");
        percentErrorLabel.setVisible(false);
        percentErrorLabel.setManaged(false);

        ComboBox<String> genreField = new ComboBox<>();
        genreField.setEditable(true);
        genreField.getItems().addAll(
                "Action", "Adventure", "RPG", "Shooter", "Strategy",
                "Sports", "Racing", "Puzzle", "Simulation", "Platformer",
                "Fighting", "Horror", "Open World"
        );
        genreField.setPromptText("Select or type a genre");

        Label genreErrorLabel = new Label();
        genreErrorLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 11px;");
        genreErrorLabel.setVisible(false);
        genreErrorLabel.setManaged(false);

        ComboBox<String> consoleField = new ComboBox<>();
        consoleField.setEditable(true);
        consoleField.getItems().addAll(
                "PC", "PlayStation 5", "PlayStation 4", "Xbox Series X/S",
                "Xbox One", "Nintendo Switch", "Nintendo Switch 2"
        );
        consoleField.setPromptText("Select or type a console");

        Label consoleErrorLabel = new Label();
        consoleErrorLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 11px;");
        consoleErrorLabel.setVisible(false);
        consoleErrorLabel.setManaged(false);

        CheckBox favoriteBox = new CheckBox("Mark as favorite");

        if (existing != null) {
            nameField.setText(existing.getName());
            hoursField.setText(String.valueOf(existing.getHoursPlayed()));
            percentField.setText(String.valueOf(existing.getCompletionPercent()));
            favoriteBox.setSelected(existing.isFavorite());
            genreField.setValue(existing.getGenre());
            consoleField.setValue(existing.getConsole());
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 20));
        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(nameErrorLabel, 1, 1);
        grid.add(duplicateErrorLabel, 1, 2);
        grid.add(new Label("Hours played:"), 0, 3);
        grid.add(hoursField, 1, 3);
        grid.add(hoursErrorLabel, 1, 4);
        grid.add(new Label("Completion:"), 0, 5);
        grid.add(percentField, 1, 5);
        grid.add(percentErrorLabel, 1, 6);
        grid.add(new Label("Genre:"), 0, 7);
        grid.add(genreField, 1, 7);
        grid.add(genreErrorLabel, 1, 8);
        grid.add(new Label("Console:"), 0, 9);
        grid.add(consoleField, 1, 9);
        grid.add(consoleErrorLabel, 1, 10);
        grid.add(favoriteBox, 1, 11);

        dialog.getDialogPane().setContent(grid);

        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);

        final int MAX_FIELD_LENGTH = 40;
        final int MAX_NAME_LENGTH = 60;
        final double MAX_HOURS = 100_000; // sanity bound; ~11 years of continuous play
        // Letters (incl. accented), digits, spaces, and common punctuation used in
        // genre/console names (e.g. "Sci-Fi", "PlayStation 5", "Beat 'em up").
        final String ALLOWED_PATTERN = "[\\p{L}\\p{N} .,'&/:()\\-]*";

        java.util.function.BiFunction<String, String, String> validateFreeText = (text, fieldLabel) -> {
            if (text.isEmpty()) {
                return null; // blank is allowed
            }
            if (text.length() > MAX_FIELD_LENGTH) {
                return fieldLabel + " must be under " + MAX_FIELD_LENGTH + " characters";
            }
            if (!text.matches(ALLOWED_PATTERN)) {
                return fieldLabel + " contains unsupported characters";
            }
            return null;
        };

        Runnable revalidate = () -> {
            String nameText = nameField.getText().trim();
            boolean nameValid;
            if (nameText.isEmpty()) {
                nameValid = false;
                nameErrorLabel.setText("Game title is required");
            } else if (nameText.length() > MAX_NAME_LENGTH) {
                nameValid = false;
                nameErrorLabel.setText("Title must be under " + MAX_NAME_LENGTH + " characters");
            } else {
                nameValid = true;
            }
            nameErrorLabel.setVisible(!nameValid);
            nameErrorLabel.setManaged(!nameValid);

            // Duplicate check: same name + same console (case-insensitive) as another
            // entry already in the library. A blank console is compared as its own
            // "no console specified" bucket, so two untitled-console entries with the
            // same name still count as duplicates.
            String consoleTextForDupCheck = consoleField.getEditor().getText().trim();
            boolean isDuplicate = nameValid && isDuplicateEntry(nameText, consoleTextForDupCheck, existing, allGames);
            if (isDuplicate) {
                duplicateErrorLabel.setText("A game with this name and console already exists in your library");
            }
            duplicateErrorLabel.setVisible(isDuplicate);
            duplicateErrorLabel.setManaged(isDuplicate);

            String hoursText = hoursField.getText().trim();
            boolean hoursValid;
            if (hoursText.isEmpty()) {
                hoursValid = true; // treated as 0
                hoursErrorLabel.setVisible(false);
                hoursErrorLabel.setManaged(false);
            } else {
                double parsed;
                boolean isNumber;
                try {
                    parsed = Double.parseDouble(hoursText);
                    isNumber = true;
                } catch (NumberFormatException e) {
                    parsed = 0;
                    isNumber = false;
                }
                if (!isNumber) {
                    hoursValid = false;
                    hoursErrorLabel.setText("Hours must be a number (e.g. 12 or 12.5)");
                } else if (!Double.isFinite(parsed)) {
                    hoursValid = false;
                    hoursErrorLabel.setText("Hours must be a regular number (not NaN or Infinity)");
                } else if (parsed < 0) {
                    hoursValid = false;
                    hoursErrorLabel.setText("Hours can't be negative");
                } else if (parsed > MAX_HOURS) {
                    hoursValid = false;
                    hoursErrorLabel.setText("Hours must be " + MAX_HOURS + " or less");
                } else {
                    hoursValid = true;
                }
                hoursErrorLabel.setVisible(!hoursValid);
                hoursErrorLabel.setManaged(!hoursValid);
            }

            String percentText = percentField.getText().trim();
            boolean percentValid;
            if (percentText.isEmpty()) {
                percentValid = true; // treated as 0
                percentErrorLabel.setVisible(false);
                percentErrorLabel.setManaged(false);
            } else {
                double parsed;
                boolean isNumber;
                try {
                    parsed = Double.parseDouble(percentText);
                    isNumber = true;
                } catch (NumberFormatException e) {
                    parsed = 0;
                    isNumber = false;
                }
                if (!isNumber) {
                    percentValid = false;
                    percentErrorLabel.setText("Completion must be a number (e.g. 50 or 72.5)");
                } else if (!Double.isFinite(parsed)) {
                    percentValid = false;
                    percentErrorLabel.setText("Completion must be a regular number (not NaN or Infinity)");
                } else if (parsed < 0 || parsed > 100) {
                    percentValid = false;
                    percentErrorLabel.setText("Completion must be between 0 and 100");
                } else {
                    percentValid = true;
                }
                percentErrorLabel.setVisible(!percentValid);
                percentErrorLabel.setManaged(!percentValid);
            }

            String genreText = genreField.getEditor().getText().trim();
            String genreError = validateFreeText.apply(genreText, "Genre");
            boolean genreValid = genreError == null;
            genreErrorLabel.setText(genreError == null ? "" : genreError);
            genreErrorLabel.setVisible(!genreValid);
            genreErrorLabel.setManaged(!genreValid);

            String consoleText = consoleField.getEditor().getText().trim();
            String consoleError = validateFreeText.apply(consoleText, "Console");
            boolean consoleValid = consoleError == null;
            consoleErrorLabel.setText(consoleError == null ? "" : consoleError);
            consoleErrorLabel.setVisible(!consoleValid);
            consoleErrorLabel.setManaged(!consoleValid);

            saveButton.setDisable(!nameValid || isDuplicate || !hoursValid || !percentValid
                    || !genreValid || !consoleValid);
        };

        nameField.textProperty().addListener((obs, oldV, newV) -> revalidate.run());
        hoursField.textProperty().addListener((obs, oldV, newV) -> revalidate.run());
        percentField.textProperty().addListener((obs, oldV, newV) -> revalidate.run());
        genreField.getEditor().textProperty().addListener((obs, oldV, newV) -> revalidate.run());
        consoleField.getEditor().textProperty().addListener((obs, oldV, newV) -> revalidate.run());
        revalidate.run();

        dialog.setResultConverter(buttonType -> {
            if (buttonType == saveButtonType) {
                String hoursText = hoursField.getText().trim();
                double hours;
                if (hoursText.isEmpty()) {
                    hours = 0;
                } else {
                    try {
                        hours = Double.parseDouble(hoursText);
                        if (!Double.isFinite(hours) || hours < 0) {
                            hours = existing != null ? existing.getHoursPlayed() : 0;
                        } else if (hours > MAX_HOURS) {
                            hours = MAX_HOURS;
                        }
                    } catch (NumberFormatException e) {
                        // Defensive fallback: validation should have blocked the Save button
                        // in this case, but if it's somehow reached, fall back safely
                        // instead of crashing.
                        hours = existing != null ? existing.getHoursPlayed() : 0;
                    }
                }

                String percentText = percentField.getText().trim();
                double percent;
                if (percentText.isEmpty()) {
                    percent = 0;
                } else {
                    try {
                        percent = Double.parseDouble(percentText);
                        if (!Double.isFinite(percent) || percent < 0 || percent > 100) {
                            // Defensive fallback: validation should already prevent this.
                            percent = existing != null ? existing.getCompletionPercent() : 0;
                        }
                    } catch (NumberFormatException e) {
                        percent = existing != null ? existing.getCompletionPercent() : 0;
                    }
                }

                String genre = sanitizeFreeText(genreField.getEditor().getText(), MAX_FIELD_LENGTH);
                String console = sanitizeFreeText(consoleField.getEditor().getText(), MAX_FIELD_LENGTH);

                if (existing != null) {
                    existing.setName(nameField.getText().trim());
                    existing.setHoursPlayed(hours);
                    existing.setCompletionPercent(percent);
                    existing.setFavorite(favoriteBox.isSelected());
                    existing.setGenre(genre);
                    existing.setConsole(console);
                    return existing;
                } else {
                    return new Game(nameField.getText().trim(), hours, favoriteBox.isSelected(),
                            LocalDateTime.now(), genre, console, percent);
                }
            }
            return null;
        });

        return dialog.showAndWait();
    }

    /**
     * True if some other game in the library already has the same name and the
     * same console (both compared case-insensitively, trimmed). The game currently
     * being edited (if any) is excluded from the comparison so editing a game
     * without changing its name/console isn't flagged as a duplicate of itself.
     */
    private static boolean isDuplicateEntry(String name, String console, Game excluding, List<Game> allGames) {
        if (allGames == null) {
            return false;
        }
        for (Game other : allGames) {
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

    /**
     * Defensive safety net: trims, truncates, and strips any unsupported characters
     * from free-text fields before they're persisted. The live validation above should
     * already prevent bad input from reaching this point, but this guarantees that
     * whatever gets saved is well-formed even if that validation is ever bypassed.
     */
    private static String sanitizeFreeText(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim().replaceAll("[^\\p{L}\\p{N} .,'&/:()\\-]", "");
        if (trimmed.length() > maxLength) {
            trimmed = trimmed.substring(0, maxLength);
        }
        return trimmed;
    }
}
