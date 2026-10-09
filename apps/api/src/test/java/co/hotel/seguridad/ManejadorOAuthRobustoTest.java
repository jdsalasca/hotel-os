package co.hotel.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import co.hotel.huespedes.ManejadorHuespedOauth2;
import co.hotel.huespedes.UsuariosHuespedRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

/**
 * Si algo revienta DENTRO del login con Google (base caída a mitad, atributo raro),
 * el dueño cae en la puerta con motivo y el motivo queda en el log: jamás un 500
 * mudo ni un silencio sin rastro. El ?error=oauth2 sin líneas en el log es lo que
 * nos tuvo dos días adivinando.
 */
@ExtendWith(MockitoExtension.class)
class ManejadorOAuthRobustoTest {

  @Mock org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Mock UsuariosHuespedRepository huespedes;

  private OAuth2AuthenticationToken token(String registro) {
    var principal = new DefaultOAuth2User(
      List.of(new SimpleGrantedAuthority("ROLE_X")),
      Map.of("sub", "999", "email", "quien@hotel.test", "given_name", "Quien",
        "name", "Quien Entero"),
      "email");
    return new OAuth2AuthenticationToken(principal,
      List.of(new SimpleGrantedAuthority("ROLE_X")), registro);
  }

  @Test
  @DisplayName("el admin cae a entrar aunque la base falle a mitad")
  void adminRobusto() throws Exception {
    when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("base caída"));
    var manejador = new ManejadorAdminOauth2(Set.of("quien@hotel.test"), jdbc);
    var res = new MockHttpServletResponse();
    manejador.onAuthenticationSuccess(new MockHttpServletRequest(), res, token("google-admin"));
    assertEquals("/admin/entrar?error=oauth2", res.getRedirectedUrl());
  }

  @Test
  @DisplayName("el huésped cae al inicio aunque su alta falle")
  void huespedRobusto() throws Exception {
    when(huespedes.porSubOCrear(anyString(), anyString(), any())).thenThrow(new RuntimeException("base caída"));
    var manejador = new ManejadorHuespedOauth2(huespedes);
    var res = new MockHttpServletResponse();
    manejador.onAuthenticationSuccess(new MockHttpServletRequest(), res, token("google-huesped"));
    assertEquals("/?error=oauth2", res.getRedirectedUrl());
  }
}
