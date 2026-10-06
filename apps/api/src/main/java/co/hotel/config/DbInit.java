package co.hotel.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DbInit {
  @Bean
  ApplicationRunner init(JdbcTemplate jdbc, @Value("${hotel.jdbc-url:jdbc:sqlite:./data/hotel.sqlite3}") String url) {
    return args -> {
      String path = url.replaceFirst("^jdbc:sqlite:", "");
      Path p = Paths.get(path);
      if (p.getParent() != null) Files.createDirectories(p.getParent());
      String sql;
      try (var in = new ClassPathResource("db/migration/V1__init.sql").getInputStream()) {
        sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      for (String st : sql.split(";")) {
        String t = st.trim();
        if (!t.isEmpty() && !t.startsWith("--")) jdbc.execute(t);
      }
      jdbc.execute("PRAGMA journal_mode=WAL");
      jdbc.execute("PRAGMA busy_timeout=5000");
      // Habitación DEMO mínima para pruebas locales (marcada DEMO, sin datos reales)
      Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM rooms", Integer.class);
      if (n != null && n == 0) jdbc.update("INSERT INTO rooms(codigo,estado) VALUES('101','ACTIVA')");
    };
  }
}
