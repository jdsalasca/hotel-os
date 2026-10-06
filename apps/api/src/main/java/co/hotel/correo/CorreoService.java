package co.hotel.correo;

import co.hotel.config.CorreoProperties;
import co.hotel.ota.HttpClienteOta;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Envío de correo con Gmail API y OAuth 2.0 de Google.
 *
 * Flujo: canje del refresh token por un access token de corta duración y envío del mensaje en
 * base64url. El access token se pide en cada envío y nunca se guarda: un token guardado en
 * memoria o en disco es una credencial que se puede filtrar.
 *
 * Regla que no se negocia: si el hotel no configuró el correo, el envío se rechaza con el motivo.
 * Un "enviado" simulado es peor que un "no configurado" honesto.
 */
@Service
public class CorreoService {
  private static final String TOKEN_POR_DEFECTO = "https://oauth2.googleapis.com/token";
  private static final String GMAIL_POR_DEFECTO = "https://gmail.googleapis.com/gmail/v1";
  private static final Pattern ACCESS_TOKEN =
      Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

  private final CorreoProperties config;
  private final HttpClienteOta http;

  public CorreoService(CorreoProperties config, HttpClienteOta http) {
    this.config = config;
    this.http = http;
  }

  public ResultadoEnvio enviar(String destinatario, String asunto, String cuerpo) {
    if (!config.estaConfigurado()) return ResultadoEnvio.fallo(config.motivoDeshabilitado());
    if (destinatario == null || !destinatario.contains("@"))
      return ResultadoEnvio.fallo("destinatario inválido");

    var token = accessToken();
    if (!token.exitosa()) return ResultadoEnvio.fallo("canje de token rechazado: " + token.detalle());

    // La respuesta es JSON; el header necesita solo el valor del access token.
    Matcher extractor = ACCESS_TOKEN.matcher(token.cuerpo());
    if (!extractor.find()) return ResultadoEnvio.fallo("la respuesta del proveedor no trae access_token");

    var envio = http.post(endpointGmail() + "/users/me/messages/send",
      Map.of("Authorization", "Bearer " + extractor.group(1), "Content-Type", "application/json"),
      "{\"raw\":\"" + codificar(crearMensaje(destinatario, asunto, cuerpo)) + "\"}");
    if (!envio.exitosa()) return ResultadoEnvio.fallo("Gmail rechazó el envío: " + envio.detalle());

    Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(envio.cuerpo());
    return m.find() ? ResultadoEnvio.ok(m.group(1)) : ResultadoEnvio.ok("sin-id");
  }

  /** Canje refresh token -> access token. */
  private HttpClienteOta.Respuesta accessToken() {
    String cuerpo = """
      {"client_id":"%s","client_secret":"%s","refresh_token":"%s","grant_type":"refresh_token"}"""
      .formatted(config.clientId(), config.clientSecret(), config.refreshToken());
    return http.post(endpointOAuth(), Map.of("Content-Type", "application/x-www-form-urlencoded"), cuerpo);
  }

  /** Mensaje RFC 2822 mínimo: From, To, Subject, fecha y cuerpo. */
  private String crearMensaje(String destinatario, String asunto, String cuerpo) {
    String fecha = DateTimeFormatter.RFC_1123_DATE_TIME.format(
      ZonedDateTime.now(ZoneId.of("UTC")));
    return """
      From: %s <%s>
      To: %s
      Subject: %s
      Date: %s
      MIME-Version: 1.0
      Content-Type: text/plain; charset=UTF-8

      %s
      """.formatted(config.remitenteNombre(), config.remitente(), destinatario, asunto, fecha, cuerpo);
  }

  /** Gmail exige base64url sin relleno. */
  private String codificar(String texto) {
    return Base64.getUrlEncoder().withoutPadding()
      .encodeToString(texto.getBytes(StandardCharsets.UTF_8));
  }

  private String endpointOAuth() {
    return vacio(config.endpointOAuth()) ? TOKEN_POR_DEFECTO : config.endpointOAuth() + "/token";
  }

  private String endpointGmail() {
    return vacio(config.endpointGmail()) ? GMAIL_POR_DEFECTO : config.endpointGmail() + "/gmail/v1";
  }

  private static boolean vacio(String v) { return v == null || v.isBlank(); }

  /** Comprobación de salud de la configuración, sin enviar nada. */
  public boolean configurado() { return config.estaConfigurado(); }

  public String motivoSiNoConfigurado() { return config.motivoDeshabilitado(); }
}