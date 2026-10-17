export type Source = { n: number; fileName: string; page?: string | null; snippet: string };

export function SourceCard({ source }: { source: Source }) {
  return <article className="source-card">
    <div className="source-head"><span className="source-number">[{source.n}]</span><strong>{source.fileName}</strong>{source.page && <span className="page-tag">Page {source.page}</span>}</div>
    <p>{source.snippet}{source.snippet.length >= 240 ? '…' : ''}</p>
  </article>;
}
