package org.china;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseClient {

    private static final String URL = "jdbc:mysql://localhost:3306/app_ai?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASSWORD = "";

    public DatabaseClient() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Driver MySQL não encontrado no classpath", e);
        }
    }

    public DbResult execute(String sql, String kind) throws SQLException {
        sql = sql.trim();
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1);
        }

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {

            if ("query".equalsIgnoreCase(kind)) {
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    return DbResult.fromResultSet(rs);
                }
            } else {
                int count = stmt.executeUpdate(sql);
                return DbResult.update(count);
            }
        }
    }

    public static class DbResult {
        private final boolean query;
        private final String text;

        private DbResult(boolean query, String text) {
            this.query = query;
            this.text = text;
        }

        public static DbResult update(int rows) {
            return new DbResult(false, "Linhas afetadas: " + rows);
        }

        public static DbResult fromResultSet(ResultSet rs) throws SQLException {
            StringBuilder sb = new StringBuilder();

            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();

            // Cabeçalho
            for (int i = 1; i <= cols; i++) {
                sb.append(meta.getColumnLabel(i));
                if (i < cols) sb.append(" | ");
            }
            sb.append("\n");
            
            for (int i = 1; i <= cols; i++) {
                sb.append("------------");
                if (i < cols) sb.append("+");
            }
            sb.append("\n");

            int rowCount = 0;
            while (rs.next()) {
                rowCount++;
                for (int i = 1; i <= cols; i++) {
                    sb.append(rs.getString(i));
                    if (i < cols) sb.append(" | ");
                }
                sb.append("\n");
                if (rowCount >= 50) {
                    sb.append("... (limite de 50 linhas atingido)\n");
                    break;
                }
            }
            if (rowCount == 0) {
                sb.append("(nenhuma linha retornada)\n");
            }

            return new DbResult(true, sb.toString());
        }

        public boolean isQuery() {
            return query;
        }

        public String getText() {
            return text;
        }
    }
}
