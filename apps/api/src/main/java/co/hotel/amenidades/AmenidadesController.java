package co.hotel.amenidades;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Servicios por tipo de habitación. Lectura pública (la web muestra lo que tiene cada oferta);
 * la marca la pone el panel. Los ids que no existen se rechazan con 400 en vez de
 * guardarlos a medias.
 */
@RestController
public class AmenidadesController {
  private final AmenidadesRepository repo;

  public AmenidadesController(AmenidadesRepository repo) {
    this.repo = repo;
  }

  @GetMapping("/api/amenidades")
  public Map<String, Object> catalogo() {
    return Map.of("amenidades", repo.catalogo());
  }

  @GetMapping("/api/amenidades/por-tipo")
  public Map<String, Object> porTipo(@RequestParam(name = "ids", required = false) String ids) {
    List<Long> tipoIds = new java.util.ArrayList<>();
    if (ids != null && !ids.isBlank()) {
      for (String parte : ids.split(",")) {
        try {
          tipoIds.add(Long.parseLong(parte.trim()));
        } catch (NumberFormatException e) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ids inválidos");
        }
      }
    }
    return Map.of("porTipo", repo.porTipos(tipoIds));
  }

  public record MarcaReq(List<Long> ids) {}

  @PutMapping("/api/admin/tipos/{id}/amenidades")
  public ResponseEntity<?> fijar(@PathVariable long id, @RequestBody MarcaReq req) {
    if (!repo.existeTipo(id))
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "tipo no existe"));
    List<Long> ids = req.ids() == null ? List.of() : req.ids().stream().distinct().toList();
    if (repo.contarConIds(ids) != ids.size())
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", "hay servicios que no existen"));
    repo.fijarParaTipo(id, ids);
    return ResponseEntity.ok(Map.of("estado", "servicios actualizados"));
  }
}
