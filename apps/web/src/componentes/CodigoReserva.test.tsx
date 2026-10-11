import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { CodigoReserva } from './CodigoReserva';

describe('CodigoReserva', () => {
  it('marca la acción de copia para darle un objetivo táctil mínimo', () => {
    render(<CodigoReserva codigo="H-DEMO-210" />);

    const boton = screen.getByRole('button', { name: 'Copiar código' });

    expect(boton.classList.contains('codigo-reserva__boton')).toBe(true);
  });
});
