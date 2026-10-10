async (page) => {
  await page.addInitScript(() => {
    const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
    const rooms = [
      { id: 1, codigo: '101', roomTypeId: 1, nombre: 'Patio', estado: 'ACTIVA' },
      { id: 2, codigo: '102', roomTypeId: 1, nombre: 'Balcón', estado: 'ACTIVA' },
      { id: 3, codigo: '201', roomTypeId: 2, nombre: 'Mirador', estado: 'ACTIVA' },
      { id: 4, codigo: '202', roomTypeId: 2, nombre: 'Jardín', estado: 'MANTENIMIENTO' },
    ];
    const calendar = rooms.map((room) => ({
      id: room.id,
      codigo: room.codigo,
      noches: Array.from({ length: 31 }, (_, index) => {
        const fecha = '2026-10-' + String(index + 1).padStart(2, '0');
        let estado = room.estado === 'MANTENIMIENTO' ? 'MANTENIMIENTO' : 'LIBRE';
        let codigoReserva = null;
        if (room.id === 1 && fecha >= '2026-10-09' && fecha < '2026-10-12') { estado = 'OCUPADA'; codigoReserva = 'H-1048'; }
        if (room.id === 2 && fecha >= '2026-10-16' && fecha < '2026-10-18') { estado = 'BLOQUEADA'; }
        return { fecha, estado, codigoReserva };
      }),
    }));
    window.fetch = async (input) => {
      const url = new URL(typeof input === 'string' ? input : input.url, location.href);
      if (!url.pathname.startsWith('/api/')) return new Response('', { status: 404 });
      if (url.pathname === '/api/admin/sesion') return json({ email: 'revision@hotel.local', nombre: 'Recepción' });
      if (url.pathname === '/api/amenidades') return json({ amenidades: [] });
      if (url.pathname === '/api/admin/habitaciones') return json(rooms);
      if (url.pathname === '/api/admin/tipos') return json([
        { id: 1, codigo: 'DOB', nombre: 'Doble', capacidadMax: 2 },
        { id: 2, codigo: 'SUI', nombre: 'Suite', capacidadMax: 4 },
      ]);
      if (url.pathname === '/api/admin/planes') return json([
        { id: 1, codigo: 'FLEX', nombre: 'Flexible', moneda: 'COP', descuentoPct: 0 },
      ]);
      if (url.pathname === '/api/admin/calendario') return json(calendar);
      if (url.pathname === '/api/admin/bloqueos') return json([]);
      return json({ error: 'Respuesta simulada no configurada' }, 404);
    };
  });
  await page.reload();
  await page.waitForTimeout(1200);
  return JSON.stringify({
    title: await page.title(),
    roomHeaders: await page.locator('tbody th[scope="row"]').allInnerTexts(),
    freeNightButtons: await page.getByRole('button', { name: /noche 2026-10-01, libre/ }).count(),
    pageText: (await page.locator('body').innerText()).slice(0, 260),
  });
}
