package co.hotel.pruebas;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;

/**
 * Cuenta sentencias SQL en tests de rendimiento: envuelve el DataSource y cuenta cada
 * prepareStatement/createStatement. Es aproximación (una sentencia = una ida a SQLite),
 * suficiente para pillar un N+1 reintroducido.
 */
public final class ContadorConsultas {
  private ContadorConsultas() {}

  public record Contador(DataSource fuente, AtomicLong total) {
    public long consultas() {
      return total.get();
    }

    public void reiniciar() {
      total.set(0);
    }
  }

  public static Contador envolver(DataSource original) {
    AtomicLong total = new AtomicLong();
    DataSource proxy = (DataSource) Proxy.newProxyInstance(
      ContadorConsultas.class.getClassLoader(), new Class<?>[] { DataSource.class },
      (p, m, args) -> {
        Object conexion = m.invoke(original, args);
        if (conexion instanceof java.sql.Connection c && m.getName().equals("getConnection")) {
          return Proxy.newProxyInstance(
            ContadorConsultas.class.getClassLoader(), new Class<?>[] { java.sql.Connection.class },
            (pc, mc, ac) -> {
              if (mc.getName().startsWith("prepareStatement")
                || mc.getName().startsWith("createStatement")
                || mc.getName().equals("prepareCall")) {
                total.incrementAndGet();
              }
              return mc.invoke(c, ac);
            });
        }
        return conexion;
      });
    return new Contador(proxy, total);
  }
}
