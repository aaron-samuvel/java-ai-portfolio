import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { SourceCard } from './SourceCard';

afterEach(cleanup);

describe('SourceCard', () => {
  it('shows citation number, file, page and excerpt', () => {
    render(<SourceCard source={{ n: 2, fileName: 'policy.pdf', page: '4', snippet: 'Returns are accepted within thirty days.' }} />);
    expect(screen.getByText('[2]')).toBeTruthy();
    expect(screen.getByText('policy.pdf')).toBeTruthy();
    expect(screen.getByText('Page 4')).toBeTruthy();
    expect(screen.getByText('Returns are accepted within thirty days.')).toBeTruthy();
  });
});
