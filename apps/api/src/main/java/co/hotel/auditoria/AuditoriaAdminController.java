package co.hotel.auditoria;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta del rastro de acciones del panel: quién cambió qué, y si le salió bien. */
@RestController
public class AuditoriaAdminController {
  private static final int MAXIMO = 500;

  private final AccionesAdminRepository repo;

  public AuditoriaAdminController(AccionesAdminRepository repo) { this.repo = repo; }

  @GetMapping("/api/admin/auditoria")
  public List<Map<String, Object>> listar(@RequestParam(defaultValue = "100") int limite) {
    return repo.recientes(Math.min(Math.max(limite, 1), MAXIMO));
  }
}