package co.hotel.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * Cuando Google no completa la vuelta, el fallo se registra con su motivo y cada
 * entrada vuelve a su puerta. Antes el lambda era mudo: el dueño veía un rebote
 * sin explicación y el log no decía nada.
 */
class FalloOauth2Test {

  private String destino(String uri) throws Exception {
    var fallo = new FalloOauth2();
    var req = new MockHttpServletRequest();
    req.setRequestURI(uri);
    var res = new MockHttpServletResponse();
    fallo.onAuthenticationFailure(req, res,
      new OAuth2AuthenticationException(new OAuth2Error("invalid_grant"), "nada que canjear"));
    return res.getRedirectedUrl();
  }

  @Test
  @DisplayName("el admin vuelve a entrar con el motivo en la URL")
  void adminVuelveAEntrar() throws Exception {
    assertEquals("/admin/entrar?error=oauth2", destino("/login/oauth2/code/google-admin"));
  }

  @Test
  @DisplayName("el huésped vuelve al inicio con el motivo en la URL")
  void huespedVuelveAlInicio() throws Exception {
    assertEquals("/?error=oauth2", destino("/login/oauth2/code/google-huesped"));
  }

  @Test
  @DisplayName("el sondeo queda marcado para no confundirlo con un fallo real")
  void sondeoMarcado() throws Exception {
    var log = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(FalloOauth2.class);
    var lista = new ListAppender<ILoggingEvent>();
    lista.start();
    log.addAppender(lista);
    try {
      var fallo = new FalloOauth2();
      var req = new MockHttpServletRequest();
      req.setRequestURI("/login/oauth2/code/google-admin");
      req.setParameter("sondeo", "1");
      var res = new MockHttpServletResponse();
      fallo.onAuthenticationFailure(req, res,
        new OAuth2AuthenticationException(new OAuth2Error("invalid_grant"), "nada que canjear"));
      assertEquals("/admin/entrar?error=oauth2", res.getRedirectedUrl());
      assertTrue(lista.list.stream()
        .anyMatch(e -> e.getFormattedMessage().contains("[sondeo]")),
        "el log debe marcar el sondeo para filtrarlo");
    } finally {
      log.detachAppender(lista);
    }
  }
}
