package co.hotel.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El panel acepta entrar con Google además de con contraseña. La contraseña no se toca: queda
 * como respaldo para cuando Google no está configurado o el hotel prefiere no usarlo.
 *
 * La allowlist decide quién es personal: estar autenticado en Google no basta, el correo tiene que
 * estar en GOOGLE_ADMIN_EMAILS. Sin eso, cualquier cuenta de Google entraría al panel.
 */
@SpringBootTest(properties = {
  "hotel.oauth2.client-id=cliente-de-prueba",
  "hotel.oauth2.client-secret=secreto-de-prueba",
  "hotel.oauth2.admin-emails=jefa@hotel.test, encargado@hotel.test"
})
@AutoConfigureMockMvc
class Oauth2AdminTest {

  private static final Path DB = crearBase();

  private static Path crearBase() {
    try {
      Path p = Files.createTempFile("hotel-oa2-", ".sqlite3");
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

  private OAuth2AuthenticationToken ficha(String registro, String email) {
    var principal = new DefaultOAuth2User(List.of(),
      Map.of("sub", "sub-" + email, "email", email, "name", "Personal"),
      "sub");
    return new OAuth2AuthenticationToken(principal, List.of(), registro);
  }

  @Test
  @DisplayName("la ruta de autorización del admin redirige a Google, no al login de mentira")
  void laRutaDeAutorizacionLlevaAGoogle() throws Exception {
    String destino = mvc.perform(get("/oauth2/authorization/google-admin"))
      .andExpect(status().isFound())
      .andReturn().getResponse().getRedirectedUrl();

    assertTrue(destino != null && destino.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"),
      "debe pedir el código a Google: " + destino);
    assertTrue(destino.contains("redirect_uri=http://localhost/login/oauth2/code/google-admin"),
      "la vuelta debe ser la del registro google-admin, no la de otro: " + destino);
  }

  @Test
  @DisplayName("un correo de la allowlist obtiene sesión con rol de administrador")
  void correoEnAllowlistEntra() throws Exception {
    var manejador = new ManejadorAdminOauth2(Set.of("jefa@hotel.test"));
    var respuesta = new MockHttpServletResponse();

    manejador.onAuthenticationSuccess(new MockHttpServletRequest(), respuesta,
      ficha("google-admin", "jefa@hotel.test"));

    var auth = SecurityContextHolder.getContext().getAuthentication();
    try {
      assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")),
        "el personal debe salir con rol ADMIN, o el panel lo rechaza: " + auth.getAuthorities());
      assertEquals("jefa@hotel.test", auth.getName(), "la auditoría debe ver el correo, no el sub");
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  @DisplayName("quien entra con Google también queda con sesión guardada")
  void laSesionDelAdminSobrevive() throws Exception {
    var manejador = new ManejadorAdminOauth2(Set.of("jefa@hotel.test"));
    var req = new MockHttpServletRequest();
    req.getSession(true);
    var respuesta = new MockHttpServletResponse();

    manejador.onAuthenticationSuccess(req, respuesta, ficha("google-admin", "jefa@hotel.test"));

    try {
      var guardado = ((org.springframework.mock.web.MockHttpSession) req.getSession(false)).getAttribute(
        org.springframework.security.web.context.HttpSessionSecurityContextRepository
          .SPRING_SECURITY_CONTEXT_KEY);
      assertTrue(guardado != null, "el panel también necesita la sesión guardada para no perder acceso");
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  @DisplayName("un correo fuera de la allowlist no entra aunque Google lo autentique")
  void correoFueraDeAllowlistNoEntra() throws Exception {
    var manejador = new ManejadorAdminOauth2(Set.of("jefa@hotel.test"));
    var respuesta = new MockHttpServletResponse();

    manejador.onAuthenticationSuccess(new MockHttpServletRequest(), respuesta,
      ficha("google-admin", "intruso@gmail.com"));

    try {
      assertEquals(302, respuesta.getStatus());
      assertEquals("/admin/entrar?error=denegado", respuesta.getRedirectedUrl());
      assertNull(SecurityContextHolder.getContext().getAuthentication(),
        "sin sesión: un redirect no sirve si el contexto queda autenticado");
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}