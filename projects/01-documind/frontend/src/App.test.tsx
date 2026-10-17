import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('DocuMind feedback flow', () => {
  it('submits the streamed answer, question, sources and selected rating', async () => {
    const requests: Array<{ url: string; init?: RequestInit }> = [];
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      requests.push({ url, init });
      const payload = url === '/api/collections'
        ? [{ id: 'collection-1', name: 'Policies', createdAt: '2026-01-01T00:00:00Z' }]
        : url.includes('/documents') ? [] : {};
      return { ok: true, json: async () => payload } as Response;
    });

    class MockEventSource {
      private listeners = new Map<string, (event: Event) => void>();
      addEventListener(type: string, listener: EventListenerOrEventListenerObject) {
        this.listeners.set(type, listener as (event: Event) => void);
      }
      close() {}
      constructor() {
        window.setTimeout(() => {
          this.listeners.get('token')?.({ data: 'Return window is 30 days.' } as MessageEvent);
          this.listeners.get('sources')?.({ data: JSON.stringify([{ n: 1, fileName: 'returns.pdf', page: '2', snippet: 'Returns within 30 days.' }]) } as MessageEvent);
          this.listeners.get('done')?.(new Event('done'));
        }, 0);
      }
    }

    vi.stubGlobal('fetch', fetchMock);
    vi.stubGlobal('EventSource', MockEventSource);
    render(<App />);

    const question = await screen.findByLabelText('Ask a question about your documents');
    fireEvent.change(question, { target: { value: 'What is the return window?' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send question' }));

    expect(await screen.findByText('Return window is 30 days.')).toBeTruthy();
    expect(screen.getByText('returns.pdf')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Yes' }));

    await waitFor(() => expect(screen.getByText('Thanks for the feedback.')).toBeTruthy());
    const feedback = requests.find((request) => request.url.endsWith('/feedback'));
    expect(feedback).toBeTruthy();
    expect(JSON.parse(String(feedback?.init?.body))).toEqual({
      question: 'What is the return window?',
      answer: 'Return window is 30 days.',
      sources: [{ n: 1, fileName: 'returns.pdf', page: '2', snippet: 'Returns within 30 days.' }],
      rating: 'UP',
    });
  });
});
