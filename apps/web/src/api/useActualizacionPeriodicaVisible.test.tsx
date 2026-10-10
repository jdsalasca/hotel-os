import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, renderHook } from '@testing-library/react';
import { useActualizacionPeriodicaVisible } from './useActualizacionPeriodicaVisible';

const visibilidadOriginal = Object.getOwnPropertyDescriptor(document, 'visibilityState');

function fijarVisibilidad(estado: DocumentVisibilityState) {
  Object.defineProperty(document, 'visibilityState', { configurable: true, value: estado });
}

describe('useActualizacionPeriodicaVisible', () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    if (visibilidadOriginal) Object.defineProperty(document, 'visibilityState', visibilidadOriginal);
    else Reflect.deleteProperty(document, 'visibilityState');
  });

  it('actualiza cada 30 segundos mientras la pestaña está visible y se detiene al desmontar', async () => {
    vi.useFakeTimers();
    fijarVisibilidad('visible');
    const actualizar = vi.fn();
    const { unmount } = renderHook(() => useActualizacionPeriodicaVisible(actualizar));

    await act(async () => { await vi.advanceTimersByTimeAsync(29_999); });
    expect(actualizar).not.toHaveBeenCalled();
    await act(async () => { await vi.advanceTimersByTimeAsync(1); });
    expect(actualizar).toHaveBeenCalledTimes(1);

    unmount();
    await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });
    expect(actualizar).toHaveBeenCalledTimes(1);
  });

  it('pausa en segundo plano y refresca al volver a una pestaña visible', async () => {
    vi.useFakeTimers();
    fijarVisibilidad('visible');
    const actualizar = vi.fn();
    renderHook(() => useActualizacionPeriodicaVisible(actualizar));

    fijarVisibilidad('hidden');
    await act(async () => { await vi.advanceTimersByTimeAsync(60_000); });
    expect(actualizar).not.toHaveBeenCalled();

    fijarVisibilidad('visible');
    await act(async () => { document.dispatchEvent(new Event('visibilitychange')); });
    expect(actualizar).toHaveBeenCalledTimes(1);
  });

  it('no instala el temporizador si la actualización no está habilitada', async () => {
    vi.useFakeTimers();
    fijarVisibilidad('visible');
    const actualizar = vi.fn();
    renderHook(() => useActualizacionPeriodicaVisible(actualizar, false));

    await act(async () => { await vi.advanceTimersByTimeAsync(60_000); });
    expect(actualizar).not.toHaveBeenCalled();
  });
});
