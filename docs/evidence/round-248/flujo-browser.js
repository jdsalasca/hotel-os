async (page) => {
  let fallosDeCarga = 1;
  await page.context().unrouteAll();
  await page.context().route(/^http:\/\/127\.0\.0\.1:4173\/api\//, async (route) => {
    const ruta = new URL(route.request().url()).pathname;
    const json = (cuerpo, status = 200) => route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(cuerpo),
    });

    if (ruta === '/api/admin/sesion') {
      return json({ email: 'admin@hotel.test', nombre: 'Admin de prueba' });
    }
    if (ruta === '/api/yo') {
      return json({ email: 'huesped@hotel.test', nombre: 'Huésped de prueba', tieneReservas: false });
    }
    if (ruta === '/api/hotel') {
      return json({ nombre: 'Hotel de prueba', nombre_corto: 'Hotel de prueba' });
    }
    if (ruta === '/api/hotel/venta') {
      return json({ a_la_venta: true });
    }
    if (ruta === '/api/mis-reservas/mensajes/nuevos' || ruta === '/api/admin/mensajes/nuevos') {
      return json({ nuevos: 0 });
    }
    if (ruta === '/api/admin/hotel-config') {
      return json({ nombre: 'Hotel de prueba', latitud: '5.65', longitud: '-73.52' });
    }
    if (ruta === '/api/admin/lugares') {
      if (fallosDeCarga > 0) {
        fallosDeCarga--;
        return json({ error: 'Error de red temporal' }, 503);
      }
      return json({
        lugares: [{
          id: 7,
          nombre: 'Mirador de prueba',
          descripcion: 'Vista sobre el valle',
          categoria: 'VISITAR',
          latitud: 5.635,
          longitud: -73.525,
          activo: true,
          metros: 240,
        }],
      });
    }
    return json({ error: 'Endpoint fuera de la fixture sintética' }, 404);
  });

  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('http://127.0.0.1:4173/admin/lugares');
  await page.getByRole('button', { name: 'Reintentar carga de lugares' }).waitFor({ state: 'visible' });
  return {
    title: await page.title(),
    heading: await page.getByRole('heading', { name: 'Lugares en el mapa' }).count(),
    retryVisible: await page.getByRole('button', { name: 'Reintentar carga de lugares' }).isVisible(),
    loadingVisible: await page.getByRole('status', { name: /Cargando lugares/ }).count(),
    layout: await page.evaluate(() => ({
      viewport: window.innerWidth,
      client: document.documentElement.clientWidth,
      scroll: document.documentElement.scrollWidth,
    })),
  };
}
