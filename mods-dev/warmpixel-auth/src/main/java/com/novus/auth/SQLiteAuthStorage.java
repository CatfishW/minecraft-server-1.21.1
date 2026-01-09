package com.novus.auth;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SQLiteAuthStorage {
    private final String url;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load SQLite JDBC driver", e);
        }
    }

    public SQLiteAuthStorage(String path) {
        this.url = "jdbc:sqlite:" + path;
        initDatabase();
    }

    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS users (" +
                         "uuid TEXT PRIMARY KEY," +
                         "username TEXT NOT NULL," +
                         "password_hash TEXT NOT NULL," +
                         "last_ip TEXT," +
                         "last_login DATETIME" +
                         ");";
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public CompletableFuture<String> getPasswordHash(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT password_hash FROM users WHERE uuid = ?";
            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("password_hash");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }, executor);
    }

    public CompletableFuture<Boolean> saveUser(UUID uuid, String username, String hash) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO users(uuid, username, password_hash) VALUES(?,?,?)";
            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, username);
                pstmt.setString(3, hash);
                pstmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> updatePassword(UUID uuid, String newHash) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE users SET password_hash = ? WHERE uuid = ?";
            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, newHash);
                pstmt.setString(2, uuid.toString());
                return pstmt.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> exists(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM users WHERE uuid = ?";
            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                ResultSet rs = pstmt.executeQuery();
                return rs.next();
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }, executor);
    }
}
