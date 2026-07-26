package com.gamelibrary;

import java.time.LocalDateTime;

/**
 * Simple data model representing one game in the library.
 */
public class Game {

    private int id = -1;
    private String name;
    private double hoursPlayed;
    private boolean favorite;
    private LocalDateTime lastPlayed;
    private String genre;
    private String console;
    private double completionPercent;

    public Game(String name, double hoursPlayed, boolean favorite, LocalDateTime lastPlayed) {
        this(name, hoursPlayed, favorite, lastPlayed, "", "", 0);
    }

    public Game(String name, double hoursPlayed, boolean favorite, LocalDateTime lastPlayed,
                String genre, String console) {
        this(name, hoursPlayed, favorite, lastPlayed, genre, console, 0);
    }

    public Game(String name, double hoursPlayed, boolean favorite, LocalDateTime lastPlayed,
                String genre, String console, double completionPercent) {
        setName(name);
        setHoursPlayed(hoursPlayed);
        this.favorite = favorite;
        this.lastPlayed = lastPlayed != null ? lastPlayed : LocalDateTime.now();
        setGenre(genre);
        setConsole(console);
        setCompletionPercent(completionPercent);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = sanitizeName(name);
    }

    public double getHoursPlayed() {
        return hoursPlayed;
    }

    public void setHoursPlayed(double hoursPlayed) {
        this.hoursPlayed = Math.max(0, hoursPlayed);
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public LocalDateTime getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(LocalDateTime lastPlayed) {
        this.lastPlayed = lastPlayed != null ? lastPlayed : LocalDateTime.now();
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = sanitize(genre);
    }

    public String getConsole() {
        return console;
    }

    public void setConsole(String console) {
        this.console = sanitize(console);
    }

    public double getCompletionPercent() {
        return completionPercent;
    }

    /** Clamps to the valid 0–100 range so an out-of-range value can never be stored. */
    public void setCompletionPercent(double completionPercent) {
        if (!Double.isFinite(completionPercent)) {
            this.completionPercent = 0;
            return;
        }
        this.completionPercent = Math.max(0, Math.min(100, completionPercent));
    }

    /** Formats completion the way it's shown in the UI, e.g. "72%" or "72.5%". */
    public String getCompletionLabel() {
        if (completionPercent == Math.floor(completionPercent)) {
            return (int) completionPercent + "%";
        }
        return completionPercent + "%";
    }

    private static final int MAX_FREE_TEXT_LENGTH = 40;
    private static final int MAX_NAME_LENGTH = 60;

    /** Trims and length-limits free-text fields so bad input can never be stored, no matter the caller. */
    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_FREE_TEXT_LENGTH) {
            trimmed = trimmed.substring(0, MAX_FREE_TEXT_LENGTH);
        }
        return trimmed;
    }

    /** Same idea as sanitize(), but for the name field: strips control characters and allows a longer length. */
    private static String sanitizeName(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("\\p{Cntrl}", "").trim();
        if (cleaned.length() > MAX_NAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_NAME_LENGTH);
        }
        return cleaned;
    }

    /** First letter of the game name, used as the tile icon. */
    public String getInitial() {
        if (name == null || name.isBlank()) {
            return "?";
        }
        return name.trim().substring(0, 1).toUpperCase();
    }

    /** Formats hours played the way the design shows it, e.g. "147h". */
    public String getHoursLabel() {
        if (hoursPlayed == Math.floor(hoursPlayed)) {
            return (int) hoursPlayed + "h";
        }
        return hoursPlayed + "h";
    }
}
