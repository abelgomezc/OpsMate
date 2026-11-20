import { describe, it, expect } from 'vitest';
import { CaseBadge, SeverityBadge, formatDate } from '@/components/cases/CaseBadge';
import { render, screen } from '@testing-library/react';

describe('CaseBadge', () => {
  it.each([
    ['NEW', 'Nuevo'],
    ['RESOLVED', 'Resuelto'],
    ['FAILED', 'Fallido'],
    ['AWAITING_APPROVAL', 'Esperando aprobación'],
  ])('renders label for %s', (status, label) => {
    render(<CaseBadge status={status} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });
});

describe('SeverityBadge', () => {
  it('renders dash when severity is null', () => {
    render(<SeverityBadge severity={null} />);
    expect(screen.getByText('—')).toBeInTheDocument();
  });

  it('renders severity label', () => {
    render(<SeverityBadge severity="ALTA" />);
    expect(screen.getByText('Alta')).toBeInTheDocument();
  });
});

describe('formatDate', () => {
  it('formats an ISO date', () => {
    const out = formatDate('2026-11-20T20:30:00Z');
    expect(out).toContain('2026');
  });

  it('returns dash for null', () => {
    expect(formatDate(null)).toBe('—');
  });
});
