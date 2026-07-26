package com.gamelibrary;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles all persistence for Game records using an embedded SQLite database.
 * The database is a single file, "gamelibrary.db", created automatically
 * in the project's working directory the first time the app runs.
 */
public class GameDao {

    private static final String DB_URL = "jdbc:sqlite:gamelibrary.db";

    private final Connection connection;

    public GameDao() {
        this.connection = openConnection();
        try {
            createTableIfNotExists();
            migrateSchema();
        } catch (SQLException e) {
            close(); // don't leak the connection if schema setup fails
            throw new RuntimeException("Could not initialize the database schema", e);
        }
    }

    private static Connection openConnection() {
        try {
            return DriverManager.getConnection(DB_URL);
        } catch (SQLException e) {
            throw new RuntimeException("Could not connect to the database", e);
        }
    }

    private void createTableIfNotExists() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS games (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "hours_played REAL NOT NULL DEFAULT 0, " +
                "favorite INTEGER NOT NULL DEFAULT 0, " +
                "last_played TEXT NOT NULL, " +
                "genre TEXT, " +
                "console TEXT, " +
                "completion_percent REAL NOT NULL DEFAULT 0)";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * Adds any columns that were introduced after a user's database file was first
     * created. CREATE TABLE IF NOT EXISTS only applies to brand-new databases, so
     * existing gamelibrary.db files need an explicit ALTER TABLE to pick up new
     * fields like completion_percent.
     */
    private void migrateSchema() throws SQLException {
        addColumnIfMissing("completion_percent", "REAL NOT NULL DEFAULT 0");
    }

    private void addColumnIfMissing(String columnName, String columnDefinition) throws SQLException {
        boolean exists = false;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(games)")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE games ADD COLUMN " + columnName + " " + columnDefinition);
            }
        }
    }

    /** Loads every game currently stored in the database. */
    public List<Game> loadAll() {
        List<Game> games = new ArrayList<>();
        String sql = "SELECT id, name, hours_played, favorite, last_played, genre, console, completion_percent " +
                "FROM games ORDER BY id";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    Game game = new Game(
                            rs.getString("name"),
                            rs.getDouble("hours_played"),
                            rs.getInt("favorite") == 1,
                            LocalDateTime.parse(rs.getString("last_played")),
                            rs.getString("genre"),
                            rs.getString("console"),
                            rs.getDouble("completion_percent")
                    );
                    game.setId(rs.getInt("id"));
                    games.add(game);
                } catch (Exception rowError) {
                    // One malformed row (e.g. corrupted date data) shouldn't prevent
                    // the rest of the library from loading. Skip it and keep going.
                    System.err.println("Skipping unreadable game row (id="
                            + safeGetInt(rs) + "): " + rowError.getMessage());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load games from the database", e);
        }
        return games;
    }

    private int safeGetInt(ResultSet rs) {
        try {
            return rs.getInt("id");
        } catch (SQLException e) {
            return -1;
        }
    }

    /** Inserts a brand-new game and sets its generated id on the passed-in object. */
    public void insert(Game game) {
        String sql = "INSERT INTO games (name, hours_played, favorite, last_played, genre, console, completion_percent) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindGameFields(ps, game);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not save the new game", e);
        }

        // The SQLite JDBC driver doesn't support Statement.RETURN_GENERATED_KEYS,
        // so we ask SQLite directly for the id of the row we just inserted.
        // This step is treated as non-fatal: the row is already safely committed
        // by this point, so failing here shouldn't make the whole add look like
        // it failed and shouldn't leave an untracked orphan row in the database.
        String idSql = "SELECT last_insert_rowid()";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(idSql)) {
            if (rs.next()) {
                game.setId(rs.getInt(1));
            }
        } catch (SQLException e) {
            System.err.println("Warning: game was saved but its new id could not be retrieved: "
                    + e.getMessage());
        }
    }

    /** Persists changes to a game that already exists in the database. */
    public void update(Game game) {
        String sql = "UPDATE games SET name = ?, hours_played = ?, favorite = ?, " +
                "last_played = ?, genre = ?, console = ?, completion_percent = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindGameFields(ps, game);
            ps.setInt(8, game.getId());
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                throw new RuntimeException(
                        "No matching game was found in the database (it may have already been deleted).");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not update the game", e);
        }
    }

    /** Removes a game from the database by id. */
    public void delete(Game game) {
        String sql = "DELETE FROM games WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, game.getId());
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                // The row was already gone (e.g. deleted elsewhere). The desired
                // end state — no such row in the database — is already true,
                // so this is worth logging but not worth failing the operation over.
                System.err.println("Warning: no database row found for game id "
                        + game.getId() + " during delete (it may have already been removed).");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not delete the game", e);
        }
    }

    private void bindGameFields(PreparedStatement ps, Game game) throws SQLException {
        ps.setString(1, game.getName());
        ps.setDouble(2, game.getHoursPlayed());
        ps.setInt(3, game.isFavorite() ? 1 : 0);
        ps.setString(4, game.getLastPlayed().toString());
        ps.setString(5, game.getGenre());
        ps.setString(6, game.getConsole());
        ps.setDouble(7, game.getCompletionPercent());
    }

    /** Closes the underlying database connection. Call this on app shutdown. */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            // Nothing useful to do on shutdown if this fails.
        }
    }
}
