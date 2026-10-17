import { FormEvent, useCallback, useEffect, useState } from 'react';
import { SourceCard, Source } from './SourceCard';

type Collection = { id: string; name: string; createdAt: string };
type Doc = { id: string; fileName: string; status: 'PROCESSING' | 'INDEXED' | 'FAILED'; errorMessage?: string | null };
type Answer = { answer: string; sources: Source[] };

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message ?? body.error ?? `Request failed (${response.status})`);
  return body as T;
}

export default function App() {
  const [collections, setCollections] = useState<Collection[]>([]);
  const [selected, setSelected] = useState('');
  const [name, setName] = useState('');
  const [documents, setDocuments] = useState<Doc[]>([]);
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState<Answer | null>(null);
  const [askedQuestion, setAskedQuestion] = useState('');
  const [feedbackSent, setFeedbackSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const loadCollections = useCallback(async () => {
    try {
      const rows = await request<Collection[]>('/api/collections');
      setCollections(rows);
      setSelected((current) => current || rows[0]?.id || '');
    } catch (err) { setError(err instanceof Error ? err.message : 'Could not load collections.'); }
  }, []);

  const loadDocuments = useCallback(async () => {
    if (!selected) return;
    try { setDocuments(await request<Doc[]>(`/api/collections/${selected}/documents`)); }
    catch (err) { setError(err instanceof Error ? err.message : 'Could not load documents.'); }
  }, [selected]);

  useEffect(() => { void loadCollections(); }, [loadCollections]);
  useEffect(() => {
    setAnswer(null);
    void loadDocuments();
    const timer = window.setInterval(() => { void loadDocuments(); }, 3000);
    return () => window.clearInterval(timer);
  }, [loadDocuments]);

  async function createCollection(event: FormEvent) {
    event.preventDefault();
    if (!name.trim()) return;
    setBusy(true); setError('');
    try {
      const created = await request<Collection>('/api/collections', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: name.trim() }),
      });
      setCollections((rows) => [created, ...rows]); setSelected(created.id); setName('');
    } catch (err) { setError(err instanceof Error ? err.message : 'Could not create collection.'); }
    finally { setBusy(false); }
  }

  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) { setError('Create or select a collection first.'); return; }
    const form = event.currentTarget;
    const file = new FormData(form).get('file');
    if (!(file instanceof File) || file.size === 0) { setError('Choose a file to upload.'); return; }
    setBusy(true); setError('');
    try {
      await request(`/api/collections/${selected}/documents`, { method: 'POST', body: new FormData(form) });
      form.reset(); await loadDocuments();
    } catch (err) { setError(err instanceof Error ? err.message : 'Upload failed.'); await loadDocuments(); }
    finally { setBusy(false); }
  }

  async function ask(event: FormEvent) {
    event.preventDefault();
    if (!selected || !question.trim()) return;
    setBusy(true); setError(''); setAnswer(null); setAskedQuestion(question.trim()); setFeedbackSent(false);
    try {
      const stream = new EventSource(`/api/collections/${selected}/ask/stream?q=${encodeURIComponent(question.trim())}`);
      await new Promise<void>((resolve, reject) => {
        stream.addEventListener('token', (event) => {
          const token = (event as MessageEvent<string>).data;
          setAnswer((current) => ({ answer: (current?.answer ?? '') + token, sources: current?.sources ?? [] }));
        });
        stream.addEventListener('sources', (event) => {
          try {
            const sources = JSON.parse((event as MessageEvent<string>).data) as Source[];
            setAnswer((current) => ({ answer: current?.answer ?? '', sources }));
          } catch { reject(new Error('Could not read source records from the answer stream.')); }
        });
        stream.addEventListener('done', () => { stream.close(); resolve(); });
        stream.onerror = () => { stream.close(); reject(new Error('The answer stream ended unexpectedly.')); };
      });
    } catch (err) { setError(err instanceof Error ? err.message : 'Could not answer the question.'); }
    finally { setBusy(false); }
  }

  async function sendFeedback(rating: 'UP' | 'DOWN') {
    if (!selected || !answer || !askedQuestion || feedbackSent) return;
    setError('');
    try {
      await request(`/api/collections/${selected}/feedback`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question: askedQuestion, answer: answer.answer, sources: answer.sources, rating }),
      });
      setFeedbackSent(true);
    } catch (err) { setError(err instanceof Error ? err.message : 'Could not save your feedback.'); }
  }

  const indexedCount = documents.filter((document) => document.status === 'INDEXED').length;
  return <main className="shell">
    <header className="topbar"><a className="brand" href="#top" aria-label="DocuMind home"><span className="brand-mark">D</span><span>documind</span></a><span className="local-tag"><span className="status-dot" /> Local workspace</span></header>
    <section className="intro" id="top"><p className="eyebrow">YOUR KNOWLEDGE, GROUNDED</p><h1>Ask your documents<br /><em>better questions.</em></h1><p className="lede">Bring your notes and policies together. DocuMind finds the passages that matter and shows where each answer came from.</p></section>
    {error && <div className="notice" role="alert"><span>{error}</span><button type="button" onClick={() => setError('')} aria-label="Dismiss message">×</button></div>}
    <section className="workspace" aria-label="Document workspace">
      <aside className="sidebar">
        <div className="section-heading"><span>COLLECTION</span><span className="count">{collections.length}</span></div>
        <label className="sr-only" htmlFor="collection">Choose collection</label><select id="collection" value={selected} onChange={(event) => setSelected(event.target.value)}><option value="">Choose a collection</option>{collections.map((collection) => <option key={collection.id} value={collection.id}>{collection.name}</option>)}</select>
        <form className="create-form" onSubmit={createCollection}><label htmlFor="collection-name">New collection</label><div className="inline-input"><input id="collection-name" value={name} onChange={(event) => setName(event.target.value)} placeholder="e.g. Course notes" maxLength={120} /><button disabled={busy || !name.trim()} aria-label="Create collection">+</button></div></form>
        <div className="section-heading docs-heading"><span>DOCUMENTS</span><span className="count">{indexedCount}</span></div>
        <form className="upload-box" onSubmit={upload}><label className="upload-label" htmlFor="file">＋ <span>Add a document</span></label><input id="file" name="file" type="file" accept=".pdf,.docx,.md,.markdown,.html,.htm,.txt" /><p>PDF, DOCX, MD, HTML or TXT<br />Up to 25 MB</p><button className="button button-secondary full" disabled={busy || !selected}>Upload file</button></form>
        <ul className="document-list" aria-label="Collection documents">{documents.map((document) => <li key={document.id}><span className={`file-icon ${document.status.toLowerCase()}`}>▤</span><span className="file-info"><span className="file-name" title={document.fileName}>{document.fileName}</span><span className={`file-status ${document.status.toLowerCase()}`}>{document.status === 'PROCESSING' ? 'Indexing…' : document.status.toLowerCase()}{document.errorMessage ? ` · ${document.errorMessage}` : ''}</span></span></li>)}{selected && documents.length === 0 && <li className="empty-small">Your documents will appear here.</li>}{!selected && <li className="empty-small">Create a collection to get started.</li>}</ul>
        <div className="privacy-note"><span>⌂</span><p>Files and model calls stay on your configured local services.</p></div>
      </aside>
      <section className="chat-panel" aria-label="Ask a question">
        <div className="chat-heading"><div><span className="eyebrow">DOCUMENT CHAT</span><h2>What would you like to know?</h2></div><span className="model-chip">OLLAMA · LOCAL</span></div>
        <div className="chat-body" aria-live="polite">{!answer && <div className="welcome-card"><div className="spark">✳</div><h3>Find the signal in your sources.</h3><p>Ask a question about the selected collection. Answers are based on retrieved passages and include source snippets when available.</p><div className="suggestions"><span>Try asking</span><button onClick={() => setQuestion('What are the key points in these documents?')} type="button">Summarise the key points</button><button onClick={() => setQuestion('Which documents describe the main requirements?')} type="button">Find the main requirements</button></div></div>}
          {answer && <article className="answer-card"><div className="answer-label"><span className="answer-icon">✳</span><span>DOCUMIND ANSWER</span></div><p className="answer-text">{answer.answer}</p>{answer.sources.length > 0 && <div className="sources"><h3>Sources <span>{answer.sources.length}</span></h3>{answer.sources.map((source) => <SourceCard key={`${source.n}-${source.fileName}`} source={source} />)}</div>}<div className="feedback" aria-label="Answer feedback"><span>{feedbackSent ? 'Thanks for the feedback.' : 'Was this answer useful?'}</span>{!feedbackSent && <><button type="button" onClick={() => void sendFeedback('UP')}>Yes</button><button type="button" onClick={() => void sendFeedback('DOWN')}>No</button></>}</div></article>}
        </div>
        <form className="question-form" onSubmit={ask}><label className="sr-only" htmlFor="question">Ask a question about your documents</label><textarea id="question" rows={2} value={question} onChange={(event) => setQuestion(event.target.value)} placeholder={selected ? 'Ask anything about this collection…' : 'Choose a collection to begin'} disabled={!selected} onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); event.currentTarget.form?.requestSubmit(); } }} /><button className="send-button" disabled={busy || !selected || !question.trim()} aria-label="Send question">{busy ? <span className="spinner" /> : '↑'}</button><p>Answers can be incomplete. Check the source passages before relying on them.</p></form>
      </section>
    </section>
    <footer><span>DOCUMIND</span><span>Private by design · Grounded in your documents</span></footer>
  </main>;
}

