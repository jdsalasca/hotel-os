package co.hotel.reservas;

import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unidad transaccional de reservas. La serialización la aporta el DataSource (BEGIN IMMEDIATE),
 * no un bloqueo de fila que SQLite no soporta. El rollback ante excepción es del gestor.
 */
@Component
public class SqliteTransactionExecutor {
  private final TransactionTemplate template;

  public SqliteTransactionExecutor(DataSource dataSource) {
    this.template = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    this.template.setIsolationLevel(TransactionTemplate.ISOLATION_SERIALIZABLE);
  }

  public <T> T enTransaccion(TransactionCallback<T> trabajo) { return template.execute(trabajo); }

  public void enTransaccion(Runnable trabajo) {
    template.executeWithoutResult(status -> trabajo.run());
  }
}