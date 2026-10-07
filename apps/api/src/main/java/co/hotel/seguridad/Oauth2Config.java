package co.hotel.seguridad;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import co.hotel.config.HotelProperties;

/**
 * Los dos registros OAuth2 con un solo Client ID. Cada uno tiene su propia URL de retorno
 * ({baseUrl}/login/oauth2/code/{registrationId}), así que en Google Cloud hay que registrar las
 * dos como URI de redireccionamiento autorizado. Sin Client ID configurado, el repositorio queda
 * vacío y el login con Google simplemente no existe: la contraseña queda como respaldo.
 */
@Configuration
public class Oauth2Config {

  /**
   * Solo existe con Client ID no vacío: InMemoryClientRegistrationRepository rechaza la lista
   * vacía, y sin credenciales el login con Google simplemente no existe.
   */
  @Bean
  @ConditionalOnExpression("!'${hotel.oauth2.client-id:}'.isBlank()")
  ClientRegistrationRepository registrosOauth2(HotelProperties props) {
    var oauth2 = props.oauth2();
    List<ClientRegistration> registros = new ArrayList<>();
    var admin = CommonOAuth2Provider.GOOGLE.getBuilder(EnrutadorOauth2.ADMIN)
      .clientId(oauth2.clientId())
      .clientSecret(oauth2.clientSecret());
    var huesped = CommonOAuth2Provider.GOOGLE.getBuilder(EnrutadorOauth2.HUESPED)
      .clientId(oauth2.clientId())
      .clientSecret(oauth2.clientSecret());
    registros.add(admin.build());
    registros.add(huesped.build());
    return new InMemoryClientRegistrationRepository(registros);
  }
}