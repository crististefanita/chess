export const reviewProgressKey = 'chesslab.review-work.v1';
const storageKey = reviewProgressKey;

/** Adjacent to the game ledger: only complete Java journals for retained game IDs. */
export function createReviewProgressStore(storage, retainedIds) {
  function read() {
    try {
      const parsed = JSON.parse(storage.getItem(storageKey) ?? '{}');
      if (parsed.version !== 1 || !parsed.records || typeof parsed.records !== 'object' || Array.isArray(parsed.records)) return {};
      return parsed.records;
    } catch { return {}; }
  }
  return {
    load(id) {
      const row = read()[id];
      return retainedIds().includes(id) && row && typeof row.journal === 'string' && typeof row.checkpoint === 'string' ? row.journal : null;
    },
    save(request, journal, expected) {
      const ids = retainedIds();
      if (!ids.includes(request.id)) return false;
      const records = read();
      if ((records[request.id]?.journal ?? null) !== expected) return false;
      const next = Object.fromEntries(Object.entries(records).filter(([id]) => ids.includes(id)));
      next[request.id] = { checkpoint: request.checkpoint, journal };
      storage.setItem(storageKey, JSON.stringify({ version: 1, records: next }));
      return true;
    },
    prune() {
      const records = read();
      const ids = retainedIds();
      if (Object.keys(records).some(id => !ids.includes(id)))
        storage.setItem(storageKey, JSON.stringify({ version: 1, records: Object.fromEntries(Object.entries(records).filter(([id]) => ids.includes(id))) }));
    },
  };
}

/** One compute at a time; a foreground final review joins the pending work. */
export function createReviewCollection({ load, compute, save, failed }) {
  const pending = new Map();
  let running;
  let last;
  const waiters = new Map();
  const key = request => request.id + '|' + request.checkpoint;

  async function pump() {
    if (running) return;
    while (pending.size) {
      const [first, request] = pending.entries().next().value;
      pending.delete(first);
      running = request;
      const id = key(request);
      try {
        let before = load(request.id);
        let writes = Promise.resolve();
        const checkpointed = journal => {
          writes = writes.then(async () => {
            if (!await save(request, journal, before)) throw new Error('Review journal no longer belongs to the durable game');
            before = journal;
          });
          void writes.catch(() => {});
          return writes;
        };
        const journal = await compute({ ...request, journal: before, checkpointed });
        if (typeof journal !== 'string' || !journal) throw new Error('Missing completed review journal');
        await writes;
        if (journal !== before) await checkpointed(journal);
        last = { key: id, journal };
        for (const waiter of waiters.get(id) ?? []) waiter.resolve(journal);
      } catch (error) {
        for (const waiter of waiters.get(id) ?? []) waiter.reject(error);
        failed(error);
      } finally {
        waiters.delete(id);
        running = null;
      }
    }
  }

  function schedule(request, foreground = false) {
    const id = key(request);
    if (last?.key === id || running && key(running) === id || pending.has(id)) return;
    // Background updates may coalesce; requests awaited by the final screen must not disappear.
    if (!foreground) for (const candidate of pending.keys()) if (!waiters.has(candidate)) pending.delete(candidate);
    pending.set(id, request);
    void pump();
  }
  return {
    submit(request) { if (request?.id && request.checkpoint) schedule(request); },
    ensure(request) {
      const id = key(request);
      if (last?.key === id) return Promise.resolve(last.journal);
      const result = new Promise((resolve, reject) => {
        const rows = waiters.get(id) ?? []; rows.push({ resolve, reject }); waiters.set(id, rows);
      });
      schedule(request, true);
      return result;
    },
  };
}
