package co.hotel.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La neutralización de fórmulas no puede mirar solo el primer carácter: un espacio, un
 * tabulador o un salto antes del `=` la burlan y la hoja igual ejecuta. Y los valores
 * legítimos (números, texto normal) no deben salir marcados.
 */
class CsvTest {

  @Test
  @DisplayName("una fórmula al inicio queda como texto")
  void formulaAlInicioEsTexto() {
    assertEquals("\"'=1+1\"", Csv.celda("=1+1"));
    assertEquals("\"'+1+1\"", Csv.celda("+1+1"));
    assertEquals("\"'-1-1\"", Csv.celda("-1-1"));
    assertEquals("\"'@x\"", Csv.celda("@x"));
  }

  @Test
  @DisplayName("espacios, tabuladores y saltos antes de la fórmula no la esconden")
  void espaciosAntesDeLaFormulaNoLaEsconden() {
    assertEquals("\"' =1+1\"", Csv.celda(" =1+1"));
    assertEquals("\"'\t=1+1\"", Csv.celda("\t=1+1"));
    assertEquals("\"'\n=1+1\"", Csv.celda("\n=1+1"));
  }

  @Test
  @DisplayName("las comillas se duplican y el texto normal no se toca")
  void comillasYTextoNormal() {
    assertEquals("\"a\"\"b\"", Csv.celda("a\"b"));
    assertEquals("\"Hotel Eridu\"", Csv.celda("Hotel Eridu"));
    assertEquals("", Csv.celda(null));
    assertEquals("\"\"", Csv.celda(""));
  }

  @Test
  @DisplayName("los números legítimos no se marcan como fórmula")
  void numerosLegitimosSinMarca() {
    assertEquals("\"123\"", Csv.celda("123"));
    assertEquals("\" 123\"", Csv.celda(" 123"));
    assertEquals("123", Csv.celda(123L));
  }
}
