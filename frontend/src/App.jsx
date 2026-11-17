import { useCallback, useEffect, useRef, useState } from "react";
import * as api from "./api.js";

const SESSION_KEY = "citewise.session";

const STATUS_LABEL = {
  UPLOADED: "Queued",
  PROCESSING: "Processing…",
  READY: "Ready",
  FAILED: "Failed",
};

const REASON_HINT = {
  LOW_SIMILARITY: "Nothing in your documents looked related to this question.",
  NOT_IN_CONTEXT: "Related text was found, but it does not answer the question.",
};

function loadSession() {
  try {
    return JSON.parse(sessionStorage.getItem(SESSION_KEY));
  } catch {
    return null;
  }
}

function AuthForm({ onSession }) {
  const [mode, setMode] = useState("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      const result =
        mode === "login" ? await api.login(email, password) : await api.register(name, email, password);
      onSession(result);
    } catch (e) {
      if (e.status === 401) setError("Wrong email or password.");
      else if (e.status === 400 && mode === "register")
        setError("Please enter a name, a valid email and a password of 8 to 72 characters.");
      else setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card auth">
      <h1>CiteWise</h1>
      <p className="muted">Ask questions about your own notes and get answers with page citations.</p>
      <form onSubmit={submit}>
        {mode === "register" && (
          <input placeholder="Name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={100} />
        )}
        <input type="email" placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <input
          type="password"
          placeholder="Password (8 or more characters)"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          minLength={8}
          maxLength={72}
        />
        <button disabled={busy}>{busy ? "Please wait…" : mode === "login" ? "Log in" : "Create account"}</button>
      </form>
      {error && <p className="error">{error}</p>}
      <button
        type="button"
        className="link"
        onClick={() => {
          setMode(mode === "login" ? "register" : "login");
          setError("");
        }}
      >
        {mode === "login" ? "New here? Create an account" : "Have an account? Log in"}
      </button>
    </div>
  );
}

function Documents({ token, onLogout }) {
  const [docs, setDocs] = useState([]);
  const [error, setError] = useState("");
  const [uploading, setUploading] = useState(false);

  const load = useCallback(async () => {
    try {
      setDocs(await api.listDocuments(token));
      setError("");
    } catch (e) {
      if (e.status === 401) onLogout();
      else setError(e.message);
    }
  }, [token, onLogout]);

  useEffect(() => {
    load();
  }, [load]);

  // while any document is still being processed, check again every 3 seconds
  const busy = docs.some((d) => d.status === "UPLOADED" || d.status === "PROCESSING");
  useEffect(() => {
    if (!busy) return undefined;
    const timer = setInterval(load, 3000);
    return () => clearInterval(timer);
  }, [busy, load]);

  async function upload(event) {
    const file = event.target.files[0];
    if (!file) return;
    setUploading(true);
    setError("");
    try {
      await api.uploadDocument(token, file);
      await load();
    } catch (e) {
      if (e.status === 401) onLogout();
      else setError(e.message);
    } finally {
      setUploading(false);
      event.target.value = "";
    }
  }

  async function retry(id) {
    try {
      await api.reprocessDocument(token, id);
      await load();
    } catch (e) {
      if (e.status === 401) onLogout();
      else setError(e.message);
    }
  }

  return (
    <section className="card">
      <h2>Your documents</h2>
      <p className="muted small">PDF, PNG or JPEG, up to 20 MB.</p>
      <input
        type="file"
        accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
        onChange={upload}
        disabled={uploading}
      />
      {uploading && <p className="muted small">Uploading…</p>}
      {error && <p className="error">{error}</p>}
      <ul className="docs">
        {docs.length === 0 && <li className="muted small">No documents yet.</li>}
        {docs.map((d) => (
          <li key={d.id} className="doc">
            <div className="docname">{d.originalFilename}</div>
            <div className="small">
              <span className={`pill ${d.status}`}>{STATUS_LABEL[d.status] || d.status}</span>{" "}
              <span className="muted">{Math.max(1, Math.round(d.fileSize / 1024))} KB</span>
            </div>
            {d.status === "FAILED" && (
              <div className="small">
                <span className="error">{d.errorMessage}</span>{" "}
                <button type="button" className="link" onClick={() => retry(d.id)}>
                  Try again
                </button>
              </div>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}

function Message({ item }) {
  const notFound = item.status === "NOT_FOUND";
  const answer = (item.answer || "").replace(/\*\*/g, "");
  const sources = item.sources || [];
  const citedCount = sources.filter((s) => s.cited).length;

  return (
    <div className="message">
      <div className="question">{item.question}</div>
      <div className={notFound ? "answer notfound" : "answer"}>
        {answer}
        {notFound && <div className="muted small">{REASON_HINT[item.reason]}</div>}
      </div>
      {item.status === "ANSWERED" && !item.hasCitations && (
        <div className="muted small">No citations were given, so treat this answer with caution.</div>
      )}
      {sources.length > 0 && (
        <details className="sources">
          <summary>
            Sources ({citedCount} cited of {sources.length} retrieved)
          </summary>
          {sources.map((s) => (
            <div key={s.n} className={s.cited ? "source cited" : "source"}>
              <div>
                <strong>
                  [{s.n}] {s.documentName}, page {s.pageNumber}
                </strong>{" "}
                <span className="muted small">
                  similarity {s.similarity.toFixed(2)}
                  {s.cited ? " · cited" : ""}
                </span>
              </div>
              <div className="snippet">{s.text.length > 320 ? `${s.text.slice(0, 320)}…` : s.text}</div>
            </div>
          ))}
        </details>
      )}
    </div>
  );
}

function Chat({ token, onLogout }) {
  const [items, setItems] = useState([]);
  const [question, setQuestion] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const bottom = useRef(null);

  useEffect(() => {
    api
      .history(token, 20)
      .then((list) => setItems([...list].reverse())) // the server sends newest first
      .catch((e) => {
        if (e.status === 401) onLogout();
      });
  }, [token, onLogout]);

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: "smooth" });
  }, [items]);

  async function submit(event) {
    event.preventDefault();
    const text = question.trim();
    if (!text || busy) return;
    setBusy(true);
    setError("");
    try {
      const reply = await api.ask(token, text);
      setItems((previous) => [...previous, reply]);
      setQuestion("");
    } catch (e) {
      if (e.status === 401) onLogout();
      else setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card">
      <h2>Ask your notes</h2>
      <div className="messages">
        {items.length === 0 && <p className="muted">Upload a document, then ask a question about it.</p>}
        {items.map((item) => (
          <Message key={item.id} item={item} />
        ))}
        <div ref={bottom} />
      </div>
      {error && <p className="error">{error}</p>}
      <form className="ask" onSubmit={submit}>
        <input
          placeholder="For example: What is the difference between a candidate key and a super key?"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          maxLength={1000}
        />
        <button disabled={busy || !question.trim()}>{busy ? "Thinking…" : "Ask"}</button>
      </form>
    </section>
  );
}

export default function App() {
  const [session, setSession] = useState(loadSession);

  const startSession = useCallback((result) => {
    const value = { token: result.token, name: result.name };
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(value));
    setSession(value);
  }, []);

  const logout = useCallback(() => {
    sessionStorage.removeItem(SESSION_KEY);
    setSession(null);
  }, []);

  if (!session) return <AuthForm onSession={startSession} />;

  return (
    <div className="app">
      <header>
        <h1>CiteWise</h1>
        <div>
          <span className="muted small">{session.name} </span>
          <button type="button" className="secondary" onClick={logout}>
            Log out
          </button>
        </div>
      </header>
      <main>
        <Documents token={session.token} onLogout={logout} />
        <Chat token={session.token} onLogout={logout} />
      </main>
    </div>
  );
}