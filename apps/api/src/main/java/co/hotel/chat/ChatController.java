package co.hotel.chat;

import co.hotel.huespedes.UsuariosHuespedRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Conversación huésped ↔ hotel por reserva. El huésped solo ve y escribe en sus reservas
 * (dueño por `usuario_id`); el panel ve y responde en todas. Leer marca lo del otro lado
 * como visto: así los contadores de "nuevos" no necesitan estado extra.
 */
@RestController
public class ChatController {
  private static final int MAX_TEXTO = 1000;

  private final ChatRepository chat;
  private final UsuariosHuespedRepository huespedes;

  public ChatController(ChatRepository chat, UsuariosHuespedRepository huespedes) {
    this.chat = chat;
    this.huespedes = huespedes;
  }

  public record MensajeReq(String texto) {}

  private Long usuarioDeSesion() {
    String email = co.hotel.huespedes.ActualCorreo.deSesion();
    if (email == null) return null;
    return huespedes.idPorEmail(email);
  }

  private Long reservaPropia(String codigo, Long usuarioId) {
    Long reservaId = chat.reservaDe(codigo);
    if (reservaId == null)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "reserva no existe");
    Long duena = chat.duenaDe(codigo);
    if (usuarioId == null || !usuarioId.equals(duena))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "reserva no existe");
    return reservaId;
  }

  /**
   * Tope antispam compartido por ambas escrituras: 30 mensajes por hora y reserva, sumando
   * los dos lados. Sin esto, un bucle llena la base; con 30 cabe una conversación real.
   */
  private static final int MAX_POR_HORA = 30;

  private boolean sinCupo(long reservaId) {
    return chat.recientes(reservaId) >= MAX_POR_HORA;
  }

  private static ResponseEntity<Map<String, String>> tope() {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
      .body(Map.of("error", "demasiados mensajes seguidos. Espera unos minutos."));
  }

  private static String exigirTexto(MensajeReq req) {
    String texto = req == null || req.texto() == null ? "" : req.texto().trim();
    if (texto.isEmpty() || texto.length() > MAX_TEXTO)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
        "el mensaje necesita entre 1 y 1000 caracteres");
    return texto;
  }

  private static Map<String, Object> vista(List<ChatRepository.Mensaje> hilo, int total) {
    return Map.of("mensajes", hilo.stream().map(m -> Map.of(
      "id", m.id(), "autor", m.autor(), "texto", m.texto(), "en", m.creadoEn(),
      "visto", m.visto())).toList(), "total", total);
  }

  @GetMapping("/api/mis-reservas/mensajes/nuevos")
  public Map<String, Object> nuevosHuesped() {
    Long usuarioId = usuarioDeSesion();
    if (usuarioId == null)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sin sesión");
    var porReserva = chat.nuevosPorReservaDeHuesped(usuarioId);
    int total = porReserva.values().stream().mapToInt(Integer::intValue).sum();
    return Map.of("nuevos", total, "porReserva", porReserva);
  }

  @GetMapping("/api/mis-reservas/{codigo}/mensajes")
  public Map<String, Object> hiloHuesped(@PathVariable String codigo) {
    Long reservaId = reservaPropia(codigo, usuarioDeSesion());
    chat.marcarVistos(reservaId, "HUESPED");
    return vista(chat.hilo(reservaId), chat.total(reservaId));
  }

  @PostMapping("/api/mis-reservas/{codigo}/mensajes")
  public ResponseEntity<?> escribirHuesped(@PathVariable String codigo,
                                          @RequestBody MensajeReq req) {
    Long reservaId = reservaPropia(codigo, usuarioDeSesion());
    if (sinCupo(reservaId)) return tope();
    long id = chat.agregar(reservaId, "HUESPED", exigirTexto(req), LocalDateTime.now().toString());
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
  }

  @GetMapping("/api/admin/reservas/{codigo}/mensajes")
  public Map<String, Object> hiloHotel(@PathVariable String codigo) {
    Long reservaId = chat.reservaDe(codigo);
    if (reservaId == null)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "reserva no existe");
    chat.marcarVistos(reservaId, "HOTEL");
    return vista(chat.hilo(reservaId), chat.total(reservaId));
  }

  @PostMapping("/api/admin/reservas/{codigo}/mensajes")
  public ResponseEntity<?> escribirHotel(@PathVariable String codigo,
                                        @RequestBody MensajeReq req) {
    Long reservaId = chat.reservaDe(codigo);
    if (reservaId == null)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "reserva no existe");
    if (sinCupo(reservaId)) return tope();
    long id = chat.agregar(reservaId, "HOTEL", exigirTexto(req), LocalDateTime.now().toString());
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
  }

  @GetMapping("/api/admin/mensajes/nuevos")
  public Map<String, Object> nuevosHotel() {
    return Map.of("nuevos", chat.nuevosParaHotel());
  }
}
