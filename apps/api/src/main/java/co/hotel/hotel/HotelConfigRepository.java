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
   * Existe una configuración de habitación activa con tarifa del mismo tipo y plan activo.
   * La disponibilidad para fechas concretas se calcula por separado.
   */
  public boolean hayVenta() {
    Integer configuraciones = jdbc.queryForObject("""
      SELECT COUNT(*)
      FROM rooms habitacion
      JOIN room_types tipo ON tipo.id=habitacion.room_type_id
      JOIN rates tarifa ON tarifa.room_type_id=tipo.id
      JOIN rate_plans plan ON plan.id=tarifa.rate_plan_id
      WHERE habitacion.estado='ACTIVA' AND plan.activo=1
      """, Integer.class);
    return configuraciones != null && configuraciones > 0;
  }
}
