# Inventario de QA integrado — R240

## Comportamientos que se firmarán

| Afirmación | Comprobación funcional | Estado visual | Evidencia |
|---|---|---|---|
| El parte se actualiza sin recargar cada 30 s mientras la pestaña está visible | Prueba con 30.000 ms simulados; smoke Playwright confirma cambios visibles y una sola navegación de documento | Datos antes y después del sondeo | `PaginaAdminHoy.test.tsx`; capturas `refresco-*-integrado.png` |
| El sondeo no reactiva la carga inicial ni hace desaparecer las filas ante un fallo | Prueba dirigida conserva la reserva y no expone el error técnico | Aviso no bloqueante junto a las filas anteriores | `PaginaAdminHoy.test.tsx`; capturas `integracion-*.png` |
| El aviso se limpia al recuperarse | Prueba: luego de un 503, el sondeo siguiente exitoso retira el aviso | Parte actualizado | `PaginaAdminHoy.test.tsx` |
| La pestaña oculta no sigue consultando | Prueba: 90 s oculto sin lecturas nuevas | Parte abierto y luego oculto | `PaginaAdminHoy.test.tsx` |
| Una respuesta de fecha anterior no reemplaza la seleccionada | Revisión de contador de petición y limpieza del efecto; la respuesta solo se aplica si conserva el identificador actual | Fecha nueva seleccionada | Revisión del código |
| Controles y tres listas caben en el viewport móvil/escritorio | Medición de overflow y rectángulos de regiones importantes | 1440×900 y 390×844 | Capturas integradas |
| No hay excepciones JavaScript sin capturar | Recolección de eventos `pageerror`; se distinguen respuestas HTTP esperadas/simuladas | Escritorio y móvil | Verificación integrada |

## Escenarios de borde

- Fallo 503 intencional del sondeo, con conservación del parte y aviso de posible antigüedad.
- Pestaña oculta durante tres intervalos simulados.
- Consulta de huésped `/api/yo` sin sesión, que devuelve 401 por diseño; el smoke de administrador no inicia sesión de huésped.
- Respuesta correcta posterior al fallo, que elimina el aviso.

Las respuestas de API del smoke son sintéticas. No se usaron cuentas, reservas, ni base de datos de producción. El 503 fue inyectado solo para QA; las dos respuestas 401 son de sesión anónima esperada.