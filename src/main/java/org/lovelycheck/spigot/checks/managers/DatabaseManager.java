package org.lovelycheck.spigot.checks.managers;

import org.lovelycheck.spigot.LovelyCheckPlugin;

import java.io.File;
import java.sql.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DatabaseManager {

    private final LovelyCheckPlugin plugin;
    private Connection connection;
    private final ExecutorService dbExecutor;

    public DatabaseManager(LovelyCheckPlugin plugin) {
        this.plugin = plugin;
        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LovelyCheck-DB-Pool");
            t.setDaemon(true);
            return t;
        });
        connect();
        createTables();
    }

    private synchronized void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            File db = new File(plugin.getDataFolder(), "data.db");
            db.getParentFile().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA foreign_keys = ON");
                s.execute("PRAGMA journal_mode = WAL");
                s.execute("PRAGMA synchronous = NORMAL");
                s.execute("PRAGMA cache_size = 1000");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to SQLite: " + e.getMessage());
        }
    }

    private synchronized void createTables() {
        if (connection == null) return;
        try (Statement s = connection.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS scans (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    type TEXT NOT NULL,
                    target_name TEXT NOT NULL,
                    target_uuid TEXT NOT NULL,
                    checker_name TEXT NOT NULL,
                    reason TEXT,
                    timestamp INTEGER NOT NULL,
                    has_detected INTEGER NOT NULL DEFAULT 0
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS hack_results (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    scan_id INTEGER NOT NULL,
                    hack_id TEXT NOT NULL,
                    hack_name TEXT NOT NULL,
                    result TEXT NOT NULL,
                    FOREIGN KEY (scan_id) REFERENCES scans(id) ON DELETE CASCADE
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS punishments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    target_name TEXT NOT NULL,
                    target_uuid TEXT NOT NULL,
                    offense INTEGER NOT NULL,
                    duration TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )""");
            s.execute("CREATE INDEX IF NOT EXISTS idx_punishments_uuid ON punishments(target_uuid)");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create tables: " + e.getMessage());
        }
    }

    public synchronized long saveScan(String type, String targetName, String targetUUID,
                                      String checkerName, String reason, boolean hasDetected) {
        if (connection == null) return -1;
        String sql = "INSERT INTO scans (type,target_name,target_uuid,checker_name,reason,timestamp,has_detected) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type);
            ps.setString(2, targetName);
            ps.setString(3, targetUUID);
            ps.setString(4, checkerName);
            ps.setString(5, reason);
            ps.setLong(6, System.currentTimeMillis());
            ps.setInt(7, hasDetected ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save scan: " + e.getMessage());
        }
        return -1;
    }

    public synchronized void saveHackResult(long scanId, String hackId, String hackName, String result) {
        if (connection == null) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hack_results (scan_id,hack_id,hack_name,result) VALUES (?,?,?,?)")) {
            ps.setLong(1, scanId);
            ps.setString(2, hackId);
            ps.setString(3, hackName);
            ps.setString(4, result);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save hack result: " + e.getMessage());
        }
    }

    public synchronized int getNextPunishmentOffense(String targetUUID) {
        if (connection == null) return 1;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM punishments WHERE target_uuid = ?")) {
            ps.setString(1, targetUUID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) + 1;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to read punishment offense count: " + e.getMessage());
        }
        return 1;
    }

    public synchronized void savePunishment(String targetName, String targetUUID,
                                            int offense, String duration, String reason) {
        if (connection == null) return;
        String sql = "INSERT INTO punishments (target_name,target_uuid,offense,duration,reason,timestamp) "
                + "VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, targetName);
            ps.setString(2, targetUUID);
            ps.setInt(3, offense);
            ps.setString(4, duration);
            ps.setString(5, reason);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save punishment: " + e.getMessage());
        }
    }

    public synchronized int getNextOffenseAndSavePunishment(String targetName, String targetUUID,
                                                            String duration, String reason) {
        if (connection == null) return 1;
        try {
            connection.setAutoCommit(false);
            int offense = 1;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM punishments WHERE target_uuid = ?")) {
                ps.setString(1, targetUUID);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        offense = rs.getInt(1) + 1;
                    }
                }
            }
            String sql = "INSERT INTO punishments (target_name,target_uuid,offense,duration,reason,timestamp) "
                    + "VALUES (?,?,?,?,?,?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, targetName);
                ps.setString(2, targetUUID);
                ps.setInt(3, offense);
                ps.setString(4, duration);
                ps.setString(5, reason);
                ps.setLong(6, System.currentTimeMillis());
                ps.executeUpdate();
            }
            connection.commit();
            return offense;
        } catch (SQLException e) {
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException ex) {
                // ignore
            }
            plugin.getLogger().warning("Failed to save punishment atomically: " + e.getMessage());
            return 1;
        } finally {
            try {
                if (connection != null) connection.setAutoCommit(true);
            } catch (SQLException e) {
                // ignore
            }
        }
    }

    public ExecutorService getExecutor() {
        return dbExecutor;
    }

    public synchronized void close() {
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbExecutor.shutdownNow();
        }
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("close: " + e.getMessage());
        }
    }
}
