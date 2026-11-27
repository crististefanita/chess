export class StockfishProtocolError extends Error {
  constructor(message, code = "STOCKFISH_PROTOCOL_ERROR") {
    super(message);
    this.name = "StockfishProtocolError";
    this.code = code;
  }
}

const UCI_MOVE = /^[a-h][1-8][a-h][1-8][qrbn]?$/;
const integer = (value, name, min, max = Number.MAX_SAFE_INTEGER) => {
  if (!Number.isSafeInteger(value) || value < min || value > max) throw new StockfishProtocolError(`Invalid ${name}`, "STOCKFISH_INVALID_REQUEST");
  return value;
};
const singleLine = (value, name) => {
  if (typeof value !== "string" || !value.trim() || /[\r\n\0]/.test(value)) throw new StockfishProtocolError(`Invalid ${name}`, "STOCKFISH_INVALID_REQUEST");
  return value.trim();
};
const fen = (value, name) => {
  const text = singleLine(value, name).split(/\s+/).join(" ");
  const fields = text.split(" ");
  if (fields.length !== 6 || !/^[prnbqkPRNBQK1-8/]+$/.test(fields[0]) || !/^[wb]$/.test(fields[1]) ||
      !/^(?:-|K?Q?k?q?)$/.test(fields[2]) || !/^(?:-|[a-h][1-8])$/.test(fields[3]) ||
      !/^\d+$/.test(fields[4]) || !/^[1-9]\d*$/.test(fields[5])) throw new StockfishProtocolError(`Invalid ${name}`, "STOCKFISH_INVALID_REQUEST");
  return text;
};
const moveList = (value, name) => {
  const list = typeof value === "string" ? (/[\r\n\0]/.test(value) ? null : value.trim().split(/\s+/).filter(Boolean)) : value;
  if (!Array.isArray(list) || list.some(move => typeof move !== "string" || !UCI_MOVE.test(move))) throw new StockfishProtocolError(`Invalid ${name}`, "STOCKFISH_INVALID_REQUEST");
  return Object.freeze([...list]);
};

/** Syntax only. The portable Java core remains responsible for position/PV legality. */
export function normalizeAnalysisRequest(request = {}) {
  if (!request || typeof request !== "object") throw new StockfishProtocolError("Invalid request", "STOCKFISH_INVALID_REQUEST");
  const initialFen = request.initialFen === undefined || request.initialFen === "startpos" ? "startpos" : fen(request.initialFen, "initialFen");
  const moves = moveList(request.moves ?? [], "moves");
  const multiPV = integer(request.multiPV ?? 3, "multiPV", 1, 256);
  const searchMoves = request.searchMoves === undefined ? undefined : moveList(request.searchMoves, "searchMoves");
  if (searchMoves && (!searchMoves.length || new Set(searchMoves).size !== searchMoves.length)) throw new StockfishProtocolError("Invalid searchMoves", "STOCKFISH_INVALID_REQUEST");
  const expectedPvCount = integer(request.expectedPvCount ?? Math.min(multiPV, searchMoves?.length ?? multiPV), "expectedPvCount", 0, multiPV);
  if (searchMoves && (expectedPvCount === 0 || expectedPvCount > searchMoves.length)) throw new StockfishProtocolError("Invalid forced-root PV count", "STOCKFISH_INVALID_REQUEST");
  if (request.signal !== undefined && (!request.signal || typeof request.signal.aborted !== "boolean" ||
      typeof request.signal.addEventListener !== "function" || typeof request.signal.removeEventListener !== "function")) throw new StockfishProtocolError("Invalid AbortSignal", "STOCKFISH_INVALID_REQUEST");
  if (request.gameKey !== undefined && typeof request.gameKey !== "string" && !Number.isSafeInteger(request.gameKey)) throw new StockfishProtocolError("Invalid gameKey", "STOCKFISH_INVALID_REQUEST");
  const normalized = {
    initialFen, moves, rootFen: fen(request.rootFen, "rootFen"), nodes: integer(request.nodes ?? 200000, "nodes", 1), multiPV,
    expectedPvCount, searchMoves, depth: request.depth === undefined ? undefined : integer(request.depth, "depth", 1, 255),
    signal: request.signal, gameKey: request.gameKey === undefined ? undefined : String(request.gameKey)
  };
  normalized.positionCommand = `position ${initialFen === "startpos" ? "startpos" : `fen ${initialFen}`}${moves.length ? ` moves ${moves.join(" ")}` : ""}`;
  normalized.goCommand = `go nodes ${normalized.nodes}${normalized.depth === undefined ? "" : ` depth ${normalized.depth}`}${searchMoves ? ` searchmoves ${searchMoves.join(" ")}` : ""}`;
  return Object.freeze(normalized);
}

function numberToken(token, field, min = 0, max = Number.MAX_SAFE_INTEGER) {
  if (typeof token !== "string" || !/^-?\d+$/.test(token)) throw new StockfishProtocolError(`Invalid UCI ${field}`);
  const value = Number(token);
  if (!Number.isSafeInteger(value) || value < min || value > max) throw new StockfishProtocolError(`Invalid UCI ${field}`);
  return value;
}

export function parseInfoLine(raw) {
  if (typeof raw !== "string" || !raw.startsWith("info ") || raw.startsWith("info string ")) return null;
  const tokens = raw.trim().split(/\s+/);
  const info = { rank: 1, multiPV: 1, bound: "EXACT", seldepth: null, nodes: null, timeMs: null, wdl: null };
  let score, pv;
  for (let index = 1; index < tokens.length; index++) {
    const token = tokens[index];
    if (token === "pv") { pv = tokens.slice(index + 1); break; }
    if (token === "depth") info.depth = numberToken(tokens[++index], "depth", 0, 255);
    else if (token === "seldepth") info.seldepth = numberToken(tokens[++index], "seldepth", 0, 10000);
    else if (token === "multipv") info.rank = info.multiPV = numberToken(tokens[++index], "multipv", 1, 256);
    else if (token === "nodes") info.nodes = numberToken(tokens[++index], "nodes");
    else if (token === "time") info.timeMs = numberToken(tokens[++index], "time");
    else if (token === "score") {
      const type = tokens[++index];
      if (type !== "cp" && type !== "mate") throw new StockfishProtocolError("Unknown UCI score type");
      score = { type, value: numberToken(tokens[++index], "score", -Number.MAX_SAFE_INTEGER) };
    } else if (token === "lowerbound" || token === "upperbound") {
      if (info.bound !== "EXACT") throw new StockfishProtocolError("Conflicting UCI score bounds");
      info.bound = token === "lowerbound" ? "LOWER" : "UPPER";
    } else if (token === "wdl") {
      info.wdl = [0, 1, 2].map(() => numberToken(tokens[++index], "wdl", 0, 1000));
      if (info.wdl.reduce((sum, value) => sum + value, 0) !== 1000) throw new StockfishProtocolError("UCI WDL does not sum to1000");
    }
  }
  // Iterative/currmove and terminal messages do not claim a scored principal variation.
  if (!score || !pv || info.depth === undefined) return null;
  if (!pv.length || pv.length > 1024 || pv.some(move => !UCI_MOVE.test(move))) throw new StockfishProtocolError("Malformed UCI principal variation");
  if (info.nodes === null || !info.wdl) throw new StockfishProtocolError("Scored UCI PV lacks nodes or requested WDL");
  return Object.freeze({ ...info, score: Object.freeze(score), cp: score.type === "cp" ? score.value : null,
    mate: score.type === "mate" ? score.value : null, wdl: Object.freeze(info.wdl), pv: Object.freeze(pv), raw });
}

export function parseBestmove(raw) {
  if (typeof raw !== "string" || !/^bestmove(?:\s|$)/.test(raw)) return null;
  const tokens = raw.trim().split(/\s+/);
  if ((tokens.length !== 2 && tokens.length !== 4) || (tokens.length === 4 && tokens[2] !== "ponder")) throw new StockfishProtocolError("Malformed UCI bestmove");
  const absent = tokens[1] === "(none)" || tokens[1] === "0000";
  if (!absent && !UCI_MOVE.test(tokens[1])) throw new StockfishProtocolError("Malformed UCI bestmove");
  if (tokens.length === 4 && !UCI_MOVE.test(tokens[3])) throw new StockfishProtocolError("Malformed UCI ponder move");
  return Object.freeze({ bestmove: absent ? null : tokens[1], ponder: tokens[3] ?? null });
}

const batchSnapshot = (depth, rows, expected, sequence) => Object.freeze({ depth, expectedPvCount: expected,
  completeBatch: rows.length === expected && new Set(rows.map(row => row.pv[0])).size === expected && rows.every((row, index) => row.rank === index + 1 && row.bound === "EXACT"),
  lines: Object.freeze([...rows]), sequence });

/** A rank1 update begins a new MultiPV round; an older completed round is retained intact. */
export class StockfishIterations {
  constructor(expectedPvCount, multiPV) {
    this.expected = expectedPvCount; this.multiPV = multiPV; this.rounds = new Map(); this.completed = new Map();
    this.sequence = 0; this.latestExactTop = [];
  }
  add(info) {
    if (info.rank > this.multiPV || info.rank > this.expected) throw new StockfishProtocolError("Unexpected UCI MultiPV rank");
    const row = Object.freeze({ ...info, sequence: ++this.sequence });
    let round = this.rounds.get(info.depth);
    if (!round || info.rank === 1) { round = { rows: new Map(), started: info.rank === 1 }; this.rounds.set(info.depth, round); }
    round.rows.set(info.rank, row);
    if (info.rank === 1 && info.bound === "EXACT") this.latestExactTop.push(row);
    const rows = [...round.rows.values()].sort((a, b) => a.rank - b.rank);
    if (round.started && rows.length === this.expected && new Set(rows.map(value => value.pv[0])).size === this.expected && rows.every((value, index) => value.rank === index + 1 && value.bound === "EXACT")) {
      this.completed.set(info.depth, batchSnapshot(info.depth, rows, this.expected, this.sequence));
    }
  }
  finish(bestmove) {
    const iterations = [...this.rounds.entries()].sort(([a], [b]) => a - b).map(([depth, round]) =>
      batchSnapshot(depth, [...round.rows.values()].sort((a, b) => a.rank - b.rank), this.expected, this.sequence));
    const completedDepthBatches = [...this.completed.values()].sort((a, b) => a.depth - b.depth);
    if (!bestmove) {
      if (this.expected !== 0) throw new StockfishProtocolError("Engine returned no bestmove for a nonterminal request");
      return { depth: 0, completeBatch: true, lines: Object.freeze([]), iterations: Object.freeze(iterations), completedDepthBatches: Object.freeze(completedDepthBatches) };
    }
    if (this.expected === 0) throw new StockfishProtocolError("Engine returned a move for a declared terminal request");
    const best = this.latestExactTop.filter(row => row.pv[0] === bestmove).sort((a, b) => b.depth - a.depth || b.sequence - a.sequence)[0];
    if (!best) throw new StockfishProtocolError("No exact top PV matches the actual bestmove", "STOCKFISH_NO_EXACT_PV");
    const completed = this.completed.get(best.depth);
    const completeBatch = !!completed && completed.lines[0].sequence === best.sequence;
    return { depth: best.depth, completeBatch, lines: completeBatch ? completed.lines : Object.freeze([best]),
      iterations: Object.freeze(iterations), completedDepthBatches: Object.freeze(completedDepthBatches) };
  }
}
