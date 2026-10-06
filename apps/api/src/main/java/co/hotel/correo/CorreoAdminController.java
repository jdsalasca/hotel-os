package co.hotel.correo;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel de correo: estado de la configuración y envío de prueba.
 *
 * Devuelve el motivo cuando el correo no está configurado, para que el hotel sepa exactamente
 * qué falta en lugar de recibir un fallo genérico.
 */
@RestController
@RequestMapping("/api/admin/correo")
public class CorreoAdminController {
  private final CorreoService correo;

  public CorreoAdminController(CorreoService correo) { this.correo = correo; }

  public record PruebaReq(String destinatario, String asunto, String cuerpo) {}

  @GetMapping("/estado")
  public Map<String, Object> estado() {
    return Map.of(
      "configurado", correo.configurado(),
      "motivo", correo.motivoSiNoConfigurado());
  }

  @PostMapping("/prueba")
  public ResponseEntity<?> prueba(@RequestBody PruebaReq req) {
    if (req.destinatario() == null || !req.destinatario().contains("@"))
      return ResponseEntity.badRequest().body(Map.of("error", "destinatario inválido"));
    ResultadoEnvio r = correo.enviar(req.destinatario(),
      req.asunto() == null ? "Prueba de envío" : req.asunto(),
      req.cuerpo() == null ? "Mensaje de prueba del sistema de reservas." : req.cuerpo());
    return ResponseEntity.status(r.enviado() ? 200 : 502).body(Map.of(
      "enviado", r.enviado(),
      "motivo", r.motivo(),
      "idMensaje", r.idMensaje() == null ? "" : r.idMensaje()));
  }
}