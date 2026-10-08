package co.hotel.hotel;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Identidad operativa del hotel en `hotel_config`. Esta tabla también guarda claves internas de
 * otros módulos; por eso el repositorio sabe leer, pero la decisión de qué se publica vive en el
 * servicio.
 */
@Repository
public class HotelConfigRepository {
  private final JdbcTemplate jdbc;

  public HotelConfigRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Map<String, String> valores() {
    Map<String, String> valores = new LinkedHashMap<>();
    jdbc.query("SELECT clave, valor FROM hotel_config ORDER BY clave",
      rs -> { valores.put(rs.getString("clave"), rs.getString("valor")); });
    return valores;
  }

  public void guardarTodos(Map<String, String> valores) {
    List<Object[]> filas = valores.entrySet().stream()
      .map(entrada -> new Object[] { entrada.getKey(), entrada.getValue(), LocalDateTime.now().toString() })
      .toList();
    jdbc.batchUpdate("INSERT INTO hotel_config(clave,valor,actualizado_en) VALUES(?,?,?) "
      + "ON CONFLICT(clave) DO UPDATE SET valor=excluded.valor, actualizado_en=excluded.actualizado_en",
      filas);
  }

  /**
   * Hay algo que vender: al menos una habitación activa y al menos una tarifa. Sin
   * precio no hay venta aunque haya cuartos, y la web lo dice en vez de fingir lleno.
   */
  public boolean hayVenta() {
    Integer cuartos = jdbc.queryForObject("SELECT COUNT(*) FROM rooms WHERE estado='ACTIVA'",
      Integer.class);
    if (cuartos == null || cuartos == 0) return false;
    Integer tarifas = jdbc.queryForObject("SELECT COUNT(*) FROM rates", Integer.class);
    return tarifas != null && tarifas > 0;
  }
}
