import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { NewCaseForm } from '@/components/cases/NewCaseForm';

vi.mock('@/api/client', () => ({
  casesApi: {
    create: vi.fn(),
  },
}));

import { casesApi } from '@/api/client';

beforeEach(() => {
  vi.resetAllMocks();
});

describe('NewCaseForm', () => {
  it('creates a case on submit', async () => {
    (casesApi.create as any).mockResolvedValue({ data: { id: 'abc-123' } });
    const onCreate = vi.fn();
    render(<NewCaseForm onCreate={onCreate} />);

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '+ Crear caso' }));
    });

    const systemInput = screen.getByPlaceholderText('ej. pagos');
    const descInput = screen.getByPlaceholderText(/describe lo que/i);

    await act(async () => {
      fireEvent.change(systemInput, { target: { value: 'pagos' } });
      fireEvent.change(descInput, { target: { value: 'pago lento' } });
      fireEvent.click(screen.getByRole('button', { name: 'Crear caso' }));
    });

    expect(casesApi.create).toHaveBeenCalledWith({
      systemId: 'pagos',
      description: 'pago lento',
    });
    expect(onCreate).toHaveBeenCalledWith({ id: 'abc-123' });
  });

  it('shows error when creation fails', async () => {
    (casesApi.create as any).mockRejectedValueOnce(
      new Error('Error: boom')
    );
    render(<NewCaseForm onCreate={vi.fn()} />);

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '+ Crear caso' }));
    });

    await act(async () => {
      fireEvent.change(screen.getByPlaceholderText('ej. pagos'), { target: { value: 'pagos' } });
      fireEvent.change(screen.getByPlaceholderText(/describe lo que/i), { target: { value: 'x' } });
      fireEvent.click(screen.getByRole('button', { name: 'Crear caso' }));
    });

    expect(screen.getByText('No se pudo crear el caso.')).toBeInTheDocument();
  });
});
