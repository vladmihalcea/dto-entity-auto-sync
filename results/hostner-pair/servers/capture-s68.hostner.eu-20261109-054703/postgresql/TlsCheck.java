import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

/**
 * Opens a connection the way the benchmark forks do (the JDBC URL of RunMatrix, without sslmode, and the
 * PgJDBC jar of the bundle) and asks the server whether that connection uses TLS.
 */
public class TlsCheck {

    public static void main(String[] args) throws Exception {
        String url = String.format("jdbc:postgresql://%s:5432/%s", System.getenv("DB_HOST"), args.length > 0 ? args[0] : "dto2entity");
        try (Connection connection = DriverManager.getConnection(url, System.getenv("JDBC_USER"), System.getenv("JDBC_PASSWORD"));
             ResultSet rs = connection.createStatement().executeQuery(
                 "select ssl, version, cipher, bits from pg_stat_ssl where pid = pg_backend_pid()")) {
            rs.next();
            System.out.printf("url=%s driver=%s ssl=%s version=%s cipher=%s bits=%d%n", url,
                connection.getMetaData().getDriverVersion(), rs.getBoolean(1), rs.getString(2), rs.getString(3), rs.getInt(4));
        }
    }
}
