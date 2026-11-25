import { normalizeAnalysisRequest, parseInfoLine, parseBestmove, StockfishIterations, StockfishProtocolError } from "./stockfish-protocol.mjs";

export const STOCKFISH_PROVIDER = Object.freeze({
  id: "stockfish-19.0.1-full-single", name: "Stockfish 19 Full single-thread", version: "19.0.1", flavor: "full-single",
  buildSha256: "991bede2f253ebb08ff01fcc48270f4b9c8e66b12d5bd5f8d6f52eb3e3b3a740",
  scriptSha256: "8071dee87312f74805a859de933e1a1ef465579ab1a51fbd597388cdce9d444d",
  sourceCommit: "0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6", network: "nn-1a298aa575a0.nnue", license: "GPL-3.0"
});

function failure(message, code, name = "StockfishError") {
  const error = new Error(message); error.name = name; error.code = code; return error;
}
const aborted = () => failure("Stockfish analysis cancelled", "ABORT_ERR", "AbortError");
const disposed = () => failure("Stockfish client is disposed", "STOCKFISH_DISPOSED");
const frozenTranscript = entries => Object.freeze(entries.map(entry => Object.freeze({ ...entry })));

/** One lazily loaded classic Worker, shared by all callers; at most one UCI search is active. */
export class StockfishClient {
  constructor({ WorkerCtor = globalThis.Worker, workerUrl = new URL("./stockfish/stockfish-19-single.js", import.meta.url),
    timers = { setTimeout: (callback, delay) => globalThis.setTimeout(callback, delay), clearTimeout: timer => globalThis.clearTimeout(timer) },
    initializationTimeoutMs = 120000, readyTimeoutMs = 10000, searchTimeoutMs = 30000, stopTimeoutMs = 1500,
    hashMiB = 32, maxTranscriptChars = 2 * 1024 * 1024 } = {}) {
    if (typeof WorkerCtor !== "function") throw failure("Web Workers are unavailable", "STOCKFISH_UNAVAILABLE");
    if (typeof timers?.setTimeout !== "function" || typeof timers?.clearTimeout !== "function") throw new TypeError("Invalid Stockfish timers");
    for (const value of [initializationTimeoutMs, readyTimeoutMs, searchTimeoutMs, stopTimeoutMs, maxTranscriptChars]) {
      if (!Number.isSafeInteger(value) || value <= 0) throw new TypeError("Stockfish limits must be positive integers");
    }
    if (!Number.isSafeInteger(hashMiB) || hashMiB < 1 || hashMiB > 1024) throw new TypeError("Invalid Stockfish hash budget");
    this._WorkerCtor = WorkerCtor; this._workerUrl = workerUrl; this._timers = timers;
    this._limits = { initializationTimeoutMs, readyTimeoutMs, searchTimeoutMs, stopTimeoutMs, maxTranscriptChars, hashMiB };
    this._queue = []; this._active = null; this._waiter = null; this._worker = null; this._epoch = 0;
    this._busy = false; this._disposed = false; this._initialized = false; this._initializing = false;
    this._identity = null; this._initTranscript = []; this._initChars = 0; this._gameKey = undefined;
  }

  analyze(input) {
    if (this._disposed) return Promise.reject(disposed());
    let request;
    try { request = normalizeAnalysisRequest(input); } catch (error) { return Promise.reject(error); }
    if (request.signal?.aborted) return Promise.reject(aborted());
    return new Promise((resolve, reject) => {
      const job = { request, resolve, reject, settled: false, cancelled: false, reason: null, transcript: [], chars: 0, searchStarted: false };
      job.onAbort = () => this._cancel(job, aborted());
      request.signal?.addEventListener("abort", job.onAbort, { once: true });
      this._queue.push(job);
      void this._pump();
    });
  }

  dispose() {
    if (this._disposed) return;
    this._disposed = true;
    this._resetWorker(disposed());
    for (const job of this._queue.splice(0)) this._reject(job, disposed());
  }

  async _pump() {
    if (this._busy || this._disposed) return;
    this._busy = true;
    try {
      while (this._queue.length && !this._disposed) {
        const job = this._queue.shift();
        if (job.cancelled || job.settled) continue;
        this._active = job;
        try {
          await this._ensureWorker(job);
          if (job.settled || this._disposed) continue;
          await this._configure(job);
          if (job.settled || this._disposed) continue;
          job.collector = new StockfishIterations(job.request.expectedPvCount, job.request.multiPV);
          job.searchStarted = true;
          const finished = this._phase("search", job, this._limits.searchTimeoutMs);
          this._send(job.request.positionCommand);
          this._send(job.request.goCommand);
          const ending = await finished;
          if (job.settled || this._disposed) continue;
          const collected = job.collector.finish(ending.bestmove);
          const request = job.request;
          this._resolve(job, Object.freeze({
            providerID: STOCKFISH_PROVIDER.id, version: STOCKFISH_PROVIDER.version, buildSha256: STOCKFISH_PROVIDER.buildSha256,
            sourceCommit: STOCKFISH_PROVIDER.sourceCommit, provider: Object.freeze({ ...STOCKFISH_PROVIDER, ...this._identity }),
            rootFen: request.rootFen, position: Object.freeze({ initialFen: request.initialFen, moves: request.moves, command: request.positionCommand }),
            history: request.moves, gameKey: request.gameKey ?? null, requestedNodes: request.nodes, requestedDepth: request.depth ?? null,
            requestedMultiPV: request.multiPV, expectedPvCount: request.expectedPvCount, searchMoves: request.searchMoves ?? null,
            options: Object.freeze({ threads: 1, hashMb: this._limits.hashMiB, nodes: request.nodes,
              multiPV: request.multiPV, limitStrength: false, depth: request.depth ?? null, searchMoves: request.searchMoves ?? null }),
            scorePerspective: "SIDE_TO_MOVE", bestmove: ending.bestmove, ponder: ending.ponder, ...collected,
            searchFinished: true, proofStatus: "ENGINE_SEARCH_NOT_EXHAUSTIVE_PROOF",
            rawUCITranscript: frozenTranscript([...this._initTranscript, ...job.transcript])
          }));
        } catch (error) {
          this._reject(job, error);
          if (error instanceof StockfishProtocolError && this._worker) this._resetWorker(error);
        } finally {
          job.request.signal?.removeEventListener("abort", job.onAbort);
          if (this._active === job) this._active = null;
        }
      }
    } finally {
      this._busy = false;
    }
  }

  async _ensureWorker(job) {
    if (this._initialized && this._worker) return;
    const epoch = ++this._epoch;
    const worker = new this._WorkerCtor(this._workerUrl); // Classic worker: the pinned upstream glue is not an ES module.
    if (job.cancelled || this._disposed) { worker.terminate(); throw job.reason ?? disposed(); }
    this._worker = worker; this._initializing = true; this._initTranscript = []; this._initChars = 0;
    this._uci = { name: "", author: "", options: new Map() };
    worker.onmessage = event => { if (epoch === this._epoch && worker === this._worker) this._message(event.data); };
    worker.onerror = event => { if (epoch === this._epoch && worker === this._worker) this._resetWorker(failure(event?.message || "Stockfish worker failed", "STOCKFISH_WORKER_ERROR")); };
    worker.onmessageerror = () => { if (epoch === this._epoch && worker === this._worker) this._resetWorker(failure("Stockfish worker message failed", "STOCKFISH_WORKER_ERROR")); };
    try {
      const handshake = this._phase("uci", job, this._limits.initializationTimeoutMs);
      this._send("uci"); await handshake;
      if (job.settled || this._disposed) throw job.reason ?? disposed();
      this._validateIdentity();
      const ready = this._phase("ready", job, this._limits.readyTimeoutMs);
      this._send("setoption name Threads value 1");
      this._send(`setoption name Hash value ${this._limits.hashMiB}`);
      this._analysisOptions(job.request.multiPV);
      this._send("isready"); await ready;
      if (job.settled || this._disposed) throw job.reason ?? disposed();
      this._initialized = true; this._gameKey = undefined;
    } finally { this._initializing = false; }
  }

  _validateIdentity() {
    const { name, author, options } = this._uci;
    if (!/^Stockfish 19(?:\b|$)/.test(name)) throw new StockfishProtocolError("Unexpected Stockfish engine identity", "STOCKFISH_ENGINE_MISMATCH");
    for (const option of ["Threads", "Hash", "MultiPV", "Skill Level", "Ponder", "UCI_ShowWDL", "UCI_LimitStrength", "EvalFile"]) {
      if (!options.has(option)) throw new StockfishProtocolError(`Required Stockfish option missing: ${option}`, "STOCKFISH_ENGINE_MISMATCH");
    }
    if (!/\bdefault nn-1a298aa575a0\.nnue(?:\s|$)/.test(options.get("EvalFile")) || !/\bmax 1(?:\s|$)/.test(options.get("Threads"))) {
      throw new StockfishProtocolError("Expected the pinned full-network single-thread build", "STOCKFISH_ENGINE_MISMATCH");
    }
    this._identity = Object.freeze({ uciName: name, uciAuthor: author, options: Object.freeze(Object.fromEntries(options)) });
  }

  _analysisOptions(multiPV) {
    this._send("setoption name UCI_LimitStrength value false");
    this._send("setoption name Skill Level value 20");
    this._send("setoption name Ponder value false");
    this._send("setoption name UCI_ShowWDL value true");
    this._send(`setoption name MultiPV value ${multiPV}`);
  }

  async _configure(job) {
    const ready = this._phase("ready", job, this._limits.readyTimeoutMs);
    if (job.request.gameKey === undefined || job.request.gameKey !== this._gameKey) this._send("ucinewgame");
    this._analysisOptions(job.request.multiPV); // Analysis never inherits weakened gameplay settings.
    this._send("isready"); await ready;
    this._gameKey = job.request.gameKey;
  }

  _phase(kind, job, timeout) {
    if (this._waiter) throw new StockfishProtocolError("Overlapping Stockfish phases");
    return new Promise((resolve, reject) => {
      const waiter = { kind, job, resolve, reject, timer: null };
      this._waiter = waiter;
      waiter.timer = this._timers.setTimeout(() => {
        if (this._waiter !== waiter) return;
        const error = failure(`Stockfish ${kind} timed out`, "STOCKFISH_TIMEOUT", "TimeoutError");
        if (kind === "search") this._cancel(job, error);
        else this._resetWorker(error);
      }, timeout);
    });
  }

  _settlePhase(error, result) {
    const waiter = this._waiter;
    if (!waiter) return;
    this._waiter = null; this._timers.clearTimeout(waiter.timer);
    if (error) waiter.reject(error); else waiter.resolve(result);
  }

  _send(line) {
    if (!this._worker) { this._settlePhase(failure("Stockfish worker unavailable", "STOCKFISH_WORKER_ERROR")); return; }
    try { this._record("send", line); this._worker.postMessage(line); }
    catch (error) { this._resetWorker(error); }
  }

  _record(direction, line) {
    if (line.length > 65536) throw new StockfishProtocolError("Stockfish output line too long", "STOCKFISH_TRANSCRIPT_LIMIT");
    const entry = Object.freeze({ direction, line });
    if (this._initializing) {
      this._initChars += line.length;
      if (this._initChars > this._limits.maxTranscriptChars) throw new StockfishProtocolError("Stockfish initialization transcript limit", "STOCKFISH_TRANSCRIPT_LIMIT");
      this._initTranscript.push(entry);
    } else if (this._active) {
      this._active.chars += line.length;
      if (this._active.chars > this._limits.maxTranscriptChars) throw new StockfishProtocolError("Stockfish search transcript limit", "STOCKFISH_TRANSCRIPT_LIMIT");
      this._active.transcript.push(entry);
    }
  }

  _message(data) {
    try {
      if (typeof data !== "string" || data.length > this._limits.maxTranscriptChars) throw new StockfishProtocolError("Invalid Stockfish worker message");
      for (const raw of data.split(/\r?\n/)) {
        const line = raw.trim(); if (!line) continue;
        this._record("receive", line);
        if (line.includes("CRITICAL ERROR") || /^Aborted\(/.test(line)) throw new StockfishProtocolError(line, "STOCKFISH_ENGINE_ERROR");
        const waiter = this._waiter;
        if (!waiter) continue;
        if (waiter.kind === "uci") {
          if (line.startsWith("id name ")) this._uci.name = line.slice(8);
          else if (line.startsWith("id author ")) this._uci.author = line.slice(10);
          else if (line.startsWith("option name ")) {
            const match = /^option name (.+?) type (.+)$/.exec(line);
            if (!match || this._uci.options.has(match[1])) throw new StockfishProtocolError("Malformed/duplicate UCI option");
            this._uci.options.set(match[1], match[2]);
          } else if (line === "uciok") this._settlePhase(null, true);
        } else if (waiter.kind === "ready") {
          if (line === "readyok") this._settlePhase(null, true);
        } else if (waiter.kind === "search") {
          const ending = parseBestmove(line);
          if (ending) this._settlePhase(null, ending);
          else if (!waiter.job.cancelled) {
            const info = parseInfoLine(line); if (info) waiter.job.collector.add(info);
          }
        }
      }
    } catch (error) { this._resetWorker(error); }
  }

  _cancel(job, reason) {
    if (job.cancelled || job.settled) return;
    job.cancelled = true; job.reason = reason; this._reject(job, reason);
    if (this._active !== job) return;
    if (job.searchStarted && this._waiter?.kind === "search") {
      const waiter = this._waiter;
      this._timers.clearTimeout(waiter.timer);
      waiter.timer = this._timers.setTimeout(() => {
        if (this._waiter === waiter) this._resetWorker(failure("Stockfish did not acknowledge stop", "STOCKFISH_STOP_TIMEOUT", "TimeoutError"));
      }, this._limits.stopTimeoutMs);
      this._send("stop"); // Drain this search's bestmove before any later position/go command.
    } else this._resetWorker(reason);
  }

  _resetWorker(error) {
    const worker = this._worker;
    this._worker = null; this._epoch++; this._initialized = false; this._gameKey = undefined;
    if (worker) {
      worker.onmessage = null; worker.onerror = null; worker.onmessageerror = null;
      try { worker.terminate(); } catch { /* The failed worker is already detached. */ }
    }
    this._settlePhase(error);
    if (this._active) this._reject(this._active, error);
  }

  _resolve(job, result) {
    if (job.settled) return;
    job.settled = true; job.request.signal?.removeEventListener("abort", job.onAbort); job.resolve(result);
  }
  _reject(job, error) {
    if (job.settled) return;
    job.settled = true; job.request.signal?.removeEventListener("abort", job.onAbort); job.reject(error);
  }
}

export const createStockfishClient = options => new StockfishClient(options);
let shared;
export function analyze(request) {
  try { shared ??= createStockfishClient(); return shared.analyze(request); } catch (error) { return Promise.reject(error); }
}
export function disposeStockfish() { shared?.dispose(); shared = undefined; }
