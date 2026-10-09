package co.hotel.reservas;

import co.hotel.auditoria.AuditoriaService;
import co.hotel.huespedes.ReservaServiceHuesped;
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
  private final ReservaServiceHuesped huespedes;

  public ReservaService(ReservaRepository repo, SqliteTransactionExecutor tx, AuditoriaService auditoria,
                        InventarioService inventario, ReservaServiceHuesped huespedes) {
    this.repo = repo;
    this.tx = tx;
    this.auditoria = auditoria;
    this.inventario = inventario;
    this.huespedes = huespedes;
  }

  public String crear(CrearReserva datos) {
    return crear(datos, null, null, null);
  }

  /**
   * Alta con precio esperado: lo que el huésped vio en la búsqueda. Si la tarifa se movió entre
   * la búsqueda y la confirmación, no se cuela el precio nuevo en silencio: se rechaza con el
   * importe vigente para que lo confirme de nuevo. La comparación va en la misma transacción que
   * el alta, porque entre comprobar y escribir la tarifa podría volver a moverse.
   */
  public String crear(CrearReserva datos, Long totalEsperadoCents, String monedaEsperada) {
    return crear(datos, totalEsperadoCents, monedaEsperada, null);
  }

  /**
   * Alta con plan esperado además del importe: el plan que la búsqueda ofreció viaja hasta el
   * alta y se verifica. Sin él, el primer plan activo se elegiría en silencio y el huésped no
   * sabría con qué plan reservó.
   */
  public String crear(CrearReserva datos, Long totalEsperadoCents, String monedaEsperada,
                      Long ratePlanIdEsperado) {
    return crear(datos, totalEsperadoCents, monedaEsperada, ratePlanIdEsperado, null);
  }

  /**
   * Alta enganchando la reserva a la cuenta en la misma unidad transaccional. El controlador
   * ya comprobó que la sesión reclama su propio correo; aquí el enganche corre junto al alta:
   * si el vínculo falla, la reserva tampoco se guarda y no queda huérfana. El reintento
   * idempotente también engancha, con el mismo resguardo de no pisar dueño.
   */
  public String crear(CrearReserva datos, Long totalEsperadoCents, String monedaEsperada,
                      Long ratePlanIdEsperado, Long usuarioId) {
    validar(datos);
    String clave = (datos.claveIdempotencia() == null || datos.claveIdempotencia().isBlank())
        ? UUID.randomUUID().toString() : datos.claveIdempotencia();

    return tx.enTransaccion(estado -> {
      // La idempotencia solo aplica al mismo huésped: otra persona que comparta la clave es una
      // reserva distinta, no un reintento. Sin el correo, se le devolvía la reserva ajena entera.
      var existente = repo.codigoPorClave(clave, datos.email());
      if (existente.isPresent()) {
        // Y solo al mismo contenido: repetir la clave con otras fechas u otra habitación no es un
        // reintento, es otra reserva. Devolver la vieja en silencio haría creer al huésped que lo
        // nuevo quedó reservado.
        var previo = repo.contenidoPorCodigo(existente.get())
          .orElseThrow(() -> new DatosInvalidosException("reserva no encontrada"));
        if (previo.roomId() != datos.roomId() || !previo.llegada().equals(datos.llegada())
            || !previo.salida().equals(datos.salida()) || previo.huespedes() != datos.huespedes()) {
          throw new ConflictoIdempotenciaException(
            "esa clave de idempotencia ya creó otra reserva: repite la petición original o usa una clave nueva");
        }
        if (usuarioId != null) huespedes.vincular(existente.get(), usuarioId);
        return existente.get();
      }

      if (repo.hayReservaSolapada(datos.roomId(), datos.llegada(), datos.salida())
          || repo.hayBloqueoSolapado(datos.roomId(), datos.llegada(), datos.salida())) {
        throw new SinDisponibilidadException("no hay disponibilidad para esas fechas");
      }

      // El precio se congela con el inventario vigente en la transacción: si el hotel cambia las
      // tarifas después, el comprobante sigue mostrando lo acordado. Y sin precio completo no hay
      // reserva: ni la habitación retirada, ni la que no alcanza la capacidad, ni la noche sin
      // tarifa están a la venta. Guardar NULL era vender sin importe.
      //
      // Con plan esperado se valida ESE plan, no el primero: comparar contra el primero
      // atrapaba al huésped en conflictos aunque aceptara, y al aceptar lo cambiaba de
      // plan en silencio. Si el plan ya no cubre, se ofrece lo vigente para reconfirmar.
      final InventarioService.PrecioAcordado acordado;
      if (ratePlanIdEsperado == null) {
        var precio = inventario.precioDe(datos.roomId(), datos.llegada(), datos.salida(), datos.huespedes());
        if (precio.isEmpty()) {
          throw new SinDisponibilidadException(
            "la habitación no está a la venta para esas fechas y huéspedes");
        }
        acordado = precio.get();
        if (totalEsperadoCents != null
            && (totalEsperadoCents.longValue() != acordado.totalCents()
              || (monedaEsperada != null && !monedaEsperada.equalsIgnoreCase(acordado.moneda())))) {
          throw new PrecioCambiadoException(acordado.totalCents(), acordado.moneda(), acordado.ratePlanId());
        }
      } else {
        var especifico = inventario.precioDe(datos.roomId(), datos.llegada(), datos.salida(),
          datos.huespedes(), ratePlanIdEsperado.longValue());
        if (especifico.isPresent()) {
          acordado = especifico.get();
          if (totalEsperadoCents != null
              && (totalEsperadoCents.longValue() != acordado.totalCents()
                || (monedaEsperada != null && !monedaEsperada.equalsIgnoreCase(acordado.moneda())))) {
            throw new PrecioCambiadoException(acordado.totalCents(), acordado.moneda(), acordado.ratePlanId());
          }
        } else {
          var vigente = inventario.precioDe(datos.roomId(), datos.llegada(), datos.salida(), datos.huespedes());
          if (vigente.isEmpty()) {
            throw new SinDisponibilidadException(
              "la habitación no está a la venta para esas fechas y huéspedes");
          }
          acordado = vigente.get();
          throw new PrecioCambiadoException(acordado.totalCents(), acordado.moneda(), acordado.ratePlanId());
        }
      }
      String codigo = generarCodigo();
      long id = repo.insertar(codigo, datos, clave,
        acordado.totalCents(), acordado.moneda(), acordado.ratePlanId());
      repo.insertarLinea(id, datos.roomId(), datos.llegada(), datos.salida());
      auditoria.cambioEstado(id, null, EstadoReserva.PENDIENTE.name(), datos.origen().name());
      if (usuarioId != null) huespedes.vincular(codigo, usuarioId);
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

  /**
   * Reasigna la reserva a otra habitación: útil cuando la asignada tiene un problema o el
   * huésped pide cambiarse. Solo reservas vigentes, habitación libre y vendible en esas fechas,
   * precio recalculado con la nueva y todo en la misma transacción con su rastro.
   */
  public Reserva reasignar(String codigo, long nuevoRoomId, String actor) {
    return tx.enTransaccion(estado -> {
      var actual = repo.porCodigo(codigo).orElseThrow(() -> new DatosInvalidosException("reserva no encontrada"));
      if (!actual.estado().vigente()) {
        throw new ExcepcionDeEstado("solo se reasigna una reserva vigente");
      }
      long id = repo.idPorCodigo(codigo).orElseThrow(() -> new DatosInvalidosException("reserva no encontrada"));
      long roomActual = repo.roomIdDe(id).orElseThrow(() -> new DatosInvalidosException("reserva sin habitación"));
      if (roomActual == nuevoRoomId) return actual;
      String codigoNuevo = repo.codigoHabitacion(nuevoRoomId).orElse(null);
      if (codigoNuevo == null) throw new DatosInvalidosException("habitación no encontrada");
      if (repo.hayReservaSolapada(nuevoRoomId, actual.llegada(), actual.salida())
          || repo.hayBloqueoSolapado(nuevoRoomId, actual.llegada(), actual.salida())) {
        throw new SinDisponibilidadException("la habitación " + codigoNuevo + " no está libre para esas fechas");
      }
      var precio = inventario.precioDe(nuevoRoomId, actual.llegada(), actual.salida(), actual.huespedes());
      if (precio.isEmpty()) {
        throw new SinDisponibilidadException(
          "la habitación " + codigoNuevo + " no está a la venta para esas fechas y huéspedes");
      }
      var acordado = precio.get();
      String codigoViejo = repo.codigoHabitacion(roomActual).orElse("?");
      repo.reasignarHabitacion(id, nuevoRoomId, actual.llegada(), actual.salida());
      repo.actualizarPrecio(codigo, acordado.totalCents(), acordado.moneda(), acordado.ratePlanId());
      auditoria.movimiento(id, actual.estado().name(),
        "habitación " + codigoViejo + " → " + codigoNuevo, actor);
      return repo.porCodigo(codigo).orElseThrow();
    });
  }

  /**
   * Cambia las fechas de la reserva: solo vigentes, sin pisar a otra reserva ni a un bloqueo, con
   * tarifa completa en las nuevas (se recalcula el precio) y todo en la misma transacción con su
   * rastro. La propia reserva no cuenta como solape.
   */
  public Reserva cambiarFechas(String codigo, java.time.LocalDate llegada, java.time.LocalDate salida,
      String actor) {
    if (llegada == null || salida == null || !llegada.isBefore(salida)) {
      throw new DatosInvalidosException("la salida debe ser posterior a la llegada");
    }
    if (llegada.isBefore(java.time.LocalDate.now())) {
      throw new DatosInvalidosException("la llegada no puede ser en el pasado");
    }
    return tx.enTransaccion(estado -> {
      var actual = repo.porCodigo(codigo).orElseThrow(() -> new DatosInvalidosException("reserva no encontrada"));
      if (!actual.estado().vigente()) {
        throw new ExcepcionDeEstado("solo se cambian las fechas de una reserva vigente");
      }
      long id = repo.idPorCodigo(codigo).orElseThrow(() -> new DatosInvalidosException("reserva no encontrada"));
      long roomId = repo.roomIdDe(id).orElseThrow(() -> new DatosInvalidosException("reserva sin habitación"));
      if (repo.hayReservaSolapadaExcepto(roomId, llegada, salida, id)
          || repo.hayBloqueoSolapado(roomId, llegada, salida)) {
        throw new SinDisponibilidadException("esas fechas no están libres para esa habitación");
      }
      var precio = inventario.precioDe(roomId, llegada, salida, actual.huespedes());
      if (precio.isEmpty()) {
        throw new SinDisponibilidadException(
          "esas fechas no están a la venta para esa habitación y huéspedes");
      }
      var acordado = precio.get();
      repo.actualizarFechas(id, codigo, llegada, salida);
      repo.actualizarPrecio(codigo, acordado.totalCents(), acordado.moneda(), acordado.ratePlanId());
      auditoria.movimiento(id, actual.estado().name(),
        "fechas " + actual.llegada() + " → " + actual.salida() + " a " + llegada + " → " + salida, actor);
      return repo.porCodigo(codigo).orElseThrow();
    });
  }

  /**
   * Cambia cuántos huespedes ocupan la reserva. El motivo real es la capacidad: subir por encima
   * de lo que cabe en la habitación se rechaza con 409, no se guarda a medias. Como las fechas no
   * se mueven, no hace falta comprobar solapes: la reserva ya ocupa su propio hueco y el tamaño
   * del grupo no cambia qué noches bloquea. El precio sí se recalcula, porque algunas tarifas
   * dependen del número de ocupantes. Todo en una transacción con su rastro.
   */
  public Reserva cambiarHuespedes(String codigo, int huespedes, String actor) {
    if (huespedes < 1) throw new DatosInvalidosException("la reserva necesita al menos un huésped");
    return tx.enTransaccion(estado -> {
      var actual = repo.porCodigo(codigo).orElseThrow(() -> new DatosInvalidosException("reserva no encontrada"));
      if (!actual.estado().vigente()) {
        throw new ExcepcionDeEstado("solo se cambian los huéspedes de una reserva vigente");
      }
      long id = repo.idPorCodigo(codigo).orElseThrow(() -> new DatosInvalidosException("reserva no encontrada"));
      long roomId = repo.roomIdDe(id).orElseThrow(() -> new DatosInvalidosException("reserva sin habitación"));
      // precioDe exige capacidad: si el grupo no cabe, sale vacío y no se toca nada.
      var precio = inventario.precioDe(roomId, actual.llegada(), actual.salida(), huespedes);
      if (precio.isEmpty()) {
        throw new SinDisponibilidadException(
          "esa habitación no admite " + huespedes + " huéspedes en esas fechas");
      }
      var acordado = precio.get();
      repo.actualizarHuespedes(id, huespedes);
      repo.actualizarPrecio(codigo, acordado.totalCents(), acordado.moneda(), acordado.ratePlanId());
      auditoria.movimiento(id, actual.estado().name(),
        "huéspedes " + actual.huespedes() + " → " + huespedes, actor);
      return repo.porCodigo(codigo).orElseThrow();
    });
  }

  public Optional<Reserva> buscar(String codigo) { return repo.porCodigo(codigo); }

  /** Identificador interno, para consultar el historial. */
  public long idDe(String codigo) {
    return repo.idPorCodigo(codigo).orElseThrow(() -> new DatosInvalidosException("reserva no encontrada"));
  }

  /**
   * Consulta pública: código + correo deben coincidir. Se compara recortado en ambos
   * lados: el dedo agrega espacios al preguntar y hay filas viejas guardadas con ellos
   * (desde la R139 ya no se guardan así). Sin esto, el huésped no ve ni cancela su
   * reserva y el comprobante dice que no existe. También cubre cancelar y comprobante,
   * que consultan por aquí.
   */
  public Optional<Reserva> consultar(String codigo, String email) {
    String pedido = email == null ? null : email.trim();
    return repo.porCodigo(codigo)
      .filter(r -> r.email() != null && pedido != null && r.email().trim().equalsIgnoreCase(pedido));
  }

  public List<Reserva> listar(int limite) { return repo.listar(limite); }

  /** Listado del panel con búsqueda por texto y filtro por estado, ambos opcionales. */
  public List<Reserva> listar(String texto, EstadoReserva estado, int limite) {
    return repo.listarFiltrado(texto, estado, limite);
  }

  /** Parte del día para la recepción: quién llega, quién se va y quién duerme. */
  public record ParteDia(List<ReservaRepository.Movimiento> llegadas,
                         List<ReservaRepository.Movimiento> salidas,
                         List<ReservaRepository.Movimiento> enCasa) {}

  public ParteDia parteDelDia(LocalDate fecha) {
    return new ParteDia(repo.llegadas(fecha), repo.salidas(fecha), repo.enCasa(fecha));
  }

  private void validar(CrearReserva datos) {
    if (datos.email() == null || !EMAIL.matcher(datos.email().trim()).matches())
      throw new DatosInvalidosException("correo electrónico inválido");
    if (datos.llegada() == null || datos.salida() == null)
      throw new DatosInvalidosException("fechas requeridas");
    if (!datos.llegada().isBefore(datos.salida()))
      throw new DatosInvalidosException("la salida debe ser posterior a la llegada");
    // Una PENDIENTE en el pasado nacería siendo no-show: se rechaza en la frontera.
    // Hoy sí vale (el walk-in existe). Vale para web, panel y lo que venga.
    if (datos.llegada().isBefore(java.time.LocalDate.now()))
      throw new DatosInvalidosException("la llegada no puede ser en el pasado");
    if (datos.huespedes() < 1)
      throw new DatosInvalidosException("número de huéspedes inválido");
    if (datos.origen() == null)
      throw new DatosInvalidosException("origen requerido");
  }

  private String generarCodigo() {
    return "H-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
  }
}