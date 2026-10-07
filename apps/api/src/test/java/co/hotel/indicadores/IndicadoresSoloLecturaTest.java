package co.hotel.indicadores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Ver el informe no puede escribir en la base. Un GET tiene que ser seguro: el navegador
 * prefetchea los enlaces, así que una escritura escondida en una lectura ocurre sola, sin que nadie
 * la pida. Y aquí no es ni caché: `indicator_results` no se consulta en ningún sitio, así que cada
 * visita llenaba la tabla de resultados que nadie lee.
 *
 * La tabla se vacía antes de cada prueba porque el guardado es un UPSERT: con las filas ya puestas,
 * ni contar filas ni mirar la ventana de tiempo lo detectan. Vacía, cualquier escritura se ve.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IndicadoresSoloLecturaTest {

  private static final Path DB = crearBase();
  private static final RequestPostProcessor ADMIN = user("admin@hotel.test").roles("ADMIN");

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-ind-solo-", ".sqlite3");
      Files.delete(p);
      return p;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void propiedades(DynamicPropertyRegistry reg) {
    reg.add("hotel.jdbc-path", () -> DB.toAbsolutePath().toString());
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired IndicadoresService indicadores;

  @BeforeEach
  void vaciarResultados() {
    jdbc.update("DELETE FROM indicator_results");
  }

  private Integer resultados() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM indicator_results", Integer.class);
  }

  @Test
  @DisplayName("mirar el informe no escribe nada en la base")
  void verElInformeNoEscribe() throws Exception {
    mvc.perform(get("/api/admin/indicadores").with(ADMIN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.indicadores").isArray());
    mvc.perform(get("/api/admin/indicadores").with(ADMIN)).andExpect(status().isOk());
    mvc.perform(get("/api/admin/indicadores.csv").with(ADMIN)).andExpect(status().isOk());

    assertEquals(0, resultados(),
      "un GET no puede escribir: el navegador prefetchea enlaces y nadie pidió estos resultados");
  }

  /** Prueba directa sobre el servicio, sin el ruido del MockMvc. */
  @Test
  @DisplayName("calcular el informe no persiste resultados")
  void calcularNoPersiste() {
    indicadores.informe("2026-10");

    assertEquals(0, resultados(),
      "indicator_results no lo consulta nadie: guardar el resultado en cada lectura es trabajo que "
        + "luego no se aprovecha");
  }

  @Test
  @DisplayName("el informe sigue devolviendo lo mismo sin la escritura")
  void elInformeSigueFuncionando() throws Exception {
    mvc.perform(get("/api/admin/indicadores").with(ADMIN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.periodo").isNotEmpty())
      .andExpect(jsonPath("$.reservasPorCanal").isMap())
      .andExpect(jsonPath("$.actividades").isArray());
  }
}