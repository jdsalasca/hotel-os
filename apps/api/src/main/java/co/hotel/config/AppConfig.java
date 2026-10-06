package co.hotel.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {
  @Bean
  public DataSource dataSource(@Value("${hotel.jdbc-url:jdbc:sqlite:./data/hotel.sqlite3}") String url) {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setDriverClassName("org.sqlite.JDBC");
    ds.setUrl(url);
    return ds;
  }
  @Bean public JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
  @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
  @Bean public String jdbcUrl(@Value("${hotel.jdbc-url:jdbc:sqlite:./data/hotel.sqlite3}") String url) { return url; }
}
