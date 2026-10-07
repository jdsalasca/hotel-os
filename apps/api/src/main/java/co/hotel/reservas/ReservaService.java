package co.hotel.reservas;

import co.hotel.auditoria.AuditoriaService;
import co.hotel.inventario.InventarioService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Reglas de reserva sobre inventario centralizado.
 *
 * La comprobación de solape y el alta ocurren en la misma transacción IMMEDIATE: si dos peticiones
 * compiten por la última habitación, una gana y la otra recibe SinDisponibilidadException, nunca
 * una sobreventa silenciosa.
 */
@Service
public class ReservaService {
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$");

  private final ReservaRepository repo;
  private final SqliteTransactionExecutor tx;
  private final AuditoriaService auditoria;
  private final InventarioService inventario;

  public ReservaService(ReservaRepository repo, SqliteTransactionExecutor tx, AuditoriaService auditoria,
                        InventarioService inventario) {
    this.repo = repo;
    this.tx = tx;
    this.auditoria = auditoria;
    this.inventario = inventario;
  }

  public String crear(CrearReserva datos) {
    validar(datos);
    String clave = (datos.claveIdempotencia() == null || datos.claveIdempotencia().isBlank())
        ? UUID.randomUUID().toString() : datos.claveIdempotencia();

    return tx.enTransaccion(estado -> {
      // La idempotencia solo aplica al mismo huésped: otra persona que comparta la clave es una
      // reserva distinta, no un reintento. Sin el correo, se le devolvía la reserva ajena entera.
      var existente = repo.codigoPorClave(clave, datos.email());
      if (existente.isPresent()) return existente.get();

      if (repo.hayReservaSolapada(datos.roomId(), datos.llegada(), datos.salida())
          || repo.hayBloqueoSolapado(datos.roomId(), datos.llegada(), datos.salida())) {
        throw new SinDisponibilidadException("no hay disponibilidad para esas fechas");
      }

      // El precio se congela con el inventario vigente en la transacción: si el hotel cambia las
      // tarifas después, el comprobante sigue mostrando lo acordado. Sin tarifa, NULL.
      var precio = inventario.precioDe(datos.roomId(), datos.llegada(), datos.salida(), datos.huespedes());
      String codigo = generarCodigo();
      long id = repo.insertar(codigo, datos, clave,
        precio.map(InventarioService.PrecioAcordado::totalCents).orElse(null),
        precio.map(InventarioService.PrecioAcordado::moneda).orElse(null),
        precio.map(InventarioService.PrecioAcordado::ratePlanId).orElse(null));
      repo.insertarLinea(id, datos.roomId(), datos.llegada(), datos.salida());
      auditoria.cambioEstado(id, null, EstadoReserva.PENDIENTE.name(), datos.origen().name());
      return codigo;
    });
  }

  /** Cambia el estado y deja rastro. Cancelar libera inventario para nuevas reservas. */
  public Reserva cambiarEstado(String codigo, EstadoReserva nuevo, String actor) {
    return tx.enTransaccion(estado -> {
      var actual = repo.porCodigo(codigo).orElseThrow(() -> new DatosInvalidosException("reserva no encontrada"));
      long id = repo.idPorCodigo(codigo).orElseThrow(() -> new DatosInvalidosException("reserva no encontrada"));
      EstadoReserva.validar(actual.estado(), nuevo);
      repo.actualizarEstado(codigo, nuevo);
      auditoria.cambioEstado(id, actual.estado().name(), nuevo.name(), actor);
      return actual.cambiarEstado(nuevo);
    });
  }

  public Optional<Reserva> buscar(String codigo) { return repo.porCodigo(codigo); }

  /** Identificador interno, para consultar el historial. */
  public long idDe(String codigo) {
    return repo.idPorCodigo(codigo).orElseThrow(() -> new DatosInvalidosException("reserva no encontrada"));
  }

  /** Consulta pública: código + correo deben coincidir. Nadie ve reservas ajenas por adivinar el código. */
  public Optional<Reserva> consultar(String codigo, String email) {
    return repo.porCodigo(codigo).filter(r -> r.email().equalsIgnoreCase(email));
  }

  public List<Reserva> listar(int limite) { return repo.listar(limite); }

  private void validar(CrearReserva datos) {
    if (datos.email() == null || !EMAIL.matcher(datos.email().trim()).matches())
      throw new DatosInvalidosException("correo electrónico inválido");
    if (datos.llegada() == null || datos.salida() == null)
      throw new DatosInvalidosException("fechas requeridas");
    if (!datos.llegada().isBefore(datos.salida()))
      throw new DatosInvalidosException("la salida debe ser posterior a la llegada");
    if (datos.huespedes() < 1)
      throw new DatosInvalidosException("número de huéspedes inválido");
    if (datos.origen() == null)
      throw new DatosInvalidosException("origen requerido");
  }

  private String generarCodigo() {
    return "H-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
  }
}