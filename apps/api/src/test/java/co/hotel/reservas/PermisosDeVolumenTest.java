package co.hotel.reservas;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * El proceso corre sin privilegios, así que un volumen creado por una imagen anterior (que corría
 * como root) deja de poder abrirse. SQLite responde SQLITE_READONLY_DIRECTORY enterrado en un
 * stack trace de Spring: sin esta comprobación, quien despliega ve un fallo que no dice qué hacer.
 */
class PermisosDeVolumenTest {

  @Test
  @DisplayName("si el directorio no es escribible, el arranque dice cómo arreglarlo")
  @DisabledOnOs(OS.WINDOWS)
  void directorioSinPermisoDaMensajeAccionable(@TempDir Path raiz) throws Exception {
    Path soloLectura = raiz.resolve("datos");
    Files.createDirectory(soloLectura);
    Files.setPosixFilePermissions(soloLectura,
      Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
             java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));

    // El proceso corre como otro usuario distinto del dueño del directorio, así que el permiso
    // de escritura no le sirve. root se lo saltaría todo y por eso el caso se salta si corre como root.
    var error = assertThrows(IllegalStateException.class,
      () -> SqliteDataSources.paraRuta(soloLectura.resolve("hotel.sqlite3").toString()));

    String mensaje = error.getMessage();
    assertTrue(mensaje.contains("no se puede escribir"), "no explica el problema: " + mensaje);
    assertTrue(mensaje.contains("chown"), "no dice cómo arreglarlo: " + mensaje);
  }
}