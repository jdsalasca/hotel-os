-- V9: Descuento porcentual por plan tarifario.
--
-- Un plan puede rebajar sus noches un porcentaje (0-100): "Flexible -15%", por ejemplo. El
-- descuento vive en el plan y no en la noche porque es una decisión comercial del plan ("esta
-- semana, todo al -15%"), no un precio distinto por fecha. Los precios por noche no cambian: el
-- descuento se aplica al totalizar, y el total sin descuento sigue visible para que el huésped
-- vea lo que se ahorra.
--
-- ADD COLUMN con DEFAULT 0: los planes existentes quedan sin descuento, que es lo honesto (nadie
-- rebajó nada). El rango 0-100 lo valida el servicio al crear y al modificar, no un CHECK, para
-- que el mensaje de error diga el motivo en vez de un genérico de SQLite.

ALTER TABLE rate_plans ADD COLUMN descuento_pct INTEGER NOT NULL DEFAULT 0;
