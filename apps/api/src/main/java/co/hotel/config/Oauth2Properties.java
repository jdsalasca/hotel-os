package co.hotel.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Login con Google. Usa el mismo Client ID que el envío de correo: en Google Cloud es una sola
 * credencial OAuth y aquí son dos registros (google-admin, google-huesped) con manejadores
 * distintos.
 *
 * @param clientId     el de Google Cloud Console (el mismo que GOOGLE_CLIENT_ID del correo)
 * @param clientSecret el de Google Cloud Console (el mismo que GOOGLE_CLIENT_SECRET del correo)
 * @param adminEmails  correos que pueden entrar al panel, separados por comas
 */
public record Oauth2Properties(
    @DefaultValue("") String clientId,
    @DefaultValue("") String clientSecret,
    @DefaultValue("") String adminEmails) {

  /** Sin Client ID no hay login con Google: queda solo la contraseña, que es el respaldo. */
  public boolean configurado() {
    return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
  }

  /** La allowlist del panel, en minúsculas y sin espacios, lista para comparar. */
  public Set<String> correosAdministradores() {
    if (adminEmails == null || adminEmails.isBlank()) return Set.of();
    return Arrays.stream(adminEmails.split(","))
      .map(String::trim).map(String::toLowerCase)
      .filter(s -> !s.isEmpty())
      .collect(Collectors.toUnmodifiableSet());
  }
}