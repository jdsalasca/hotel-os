package co.hotel.hotel;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

/**
 * Identidad operativa del hotel. La web pública puede mostrar nombre y contacto, pero nunca puede
 * ver claves internas ni decisiones pendientes de otros módulos.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HotelConfigControllerTest {

  private static final Path DB = crearBase();
  private static final RequestPostProcessor ADMIN = user("admin@hotel.test").roles("ADMIN");
  private static final RequestPostProcessor USUARIO = user("persona@hotel.test").roles("USER");
  private static final ObjectMapper JSON = new ObjectMapper();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-config-", ".sqlite3");
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

  private Map<String, String> configuracionValida() {
    Map<String, String> valores = new LinkedHashMap<>();
    valores.put("nombre", "Hotel Prueba Leyva");
    valores.put("contacto_email", "hotel@ejemplo.com");
    valores.put("contacto_telefono", "+57 320 000 0000");
    valores.put("direccion", "Calle 1 # 2-3, Villa de Leyva");
    valores.put("hora_entrada", "15:00");
    valores.put("hora_salida", "12:00");
    valores.put("politica_cancelacion", "Cancelación gratuita hasta 24 horas antes.");
    valores.put("zona_horaria", "America/Bogota");
    valores.put("moneda", "COP");
    return valores;
  }

  private void guardar(Map<String, String> valores) throws Exception {
    mvc.perform(post("/api/admin/hotel-config").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(valores)))
      .andExpect(status().isOk());
  }

  @Test
  @DisplayName("el hotel guarda su identidad y la web pública la puede leer")
  void guardarYPublicarIdentidad() throws Exception {
    guardar(configuracionValida());

    mvc.perform(get("/api/admin/hotel-config").with(ADMIN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.nombre").value("Hotel Prueba Leyva"))
      .andExpect(jsonPath("$.zona_horaria").value("America/Bogota"));

    mvc.perform(get("/api/hotel"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.nombre").value("Hotel Prueba Leyva"))
      .andExpect(jsonPath("$.contacto_email").value("hotel@ejemplo.com"));
  }

  @Test
  @DisplayName("la web pública no ve claves internas de otros módulos")
  void loPublicoNoFiltraClavesInternas() throws Exception {
    mvc.perform(post("/api/admin/indicadores/inventario-esperado").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("habitaciones", 7))))
      .andExpect(status().isOk());

    mvc.perform(get("/api/hotel"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.inventario_esperado").doesNotExist());
  }

  @Test
  @DisplayName("una clave desconocida se rechaza sin guardar nada")
  void claveDesconocidaSeRechaza() throws Exception {
    mvc.perform(post("/api/admin/hotel-config").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("ADMIN_INIT_TOKEN", "secreto"))))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("un correo, hora o zona inválidos se rechazan con mensaje")
  void valoresInvalidosSeRechazan() throws Exception {
    mvc.perform(post("/api/admin/hotel-config").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("contacto_email", "no-es-correo"))))
      .andExpect(status().isBadRequest());

    mvc.perform(post("/api/admin/hotel-config").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("hora_entrada", "25:00"))))
      .andExpect(status().isBadRequest());

    mvc.perform(post("/api/admin/hotel-config").with(ADMIN).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("zona_horaria", "America/No_Existe"))))
      .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("la configuración administrativa exige rol de administrador")
  void configuracionExigeAdministrador() throws Exception {
    mvc.perform(get("/api/admin/hotel-config"))
      .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/admin/hotel-config").with(USUARIO))
      .andExpect(status().isForbidden());
    mvc.perform(post("/api/admin/hotel-config").with(USUARIO).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("nombre", "Hotel Ajeno"))))
      .andExpect(status().isForbidden());
  }
}
