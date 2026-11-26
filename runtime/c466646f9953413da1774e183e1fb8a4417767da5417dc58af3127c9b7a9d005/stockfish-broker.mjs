import { createStockfishClient, STOCKFISH_PROVIDER } from './stockfish-client.mjs';
import { normalizeAnalysisRequest } from './stockfish-protocol.mjs';

let sharedClient;
let caches = new WeakMap();
let serial = 0;
const owners = new Set();
const clients = new Set();
const privateMessage = data => typeof data?.kind === 'string' && data.kind.startsWith('j7-stockfish-');
const clone = value => structuredClone(value);
const capture = options => options === true || options?.capture === true;

function cacheFor(client) {
    let cache = caches.get(client);
    if (!cache) { cache = new Map(); caches.set(client, cache); }
    return cache;
}

function key(request) {
    return JSON.stringify([STOCKFISH_PROVIDER.id, STOCKFISH_PROVIDER.buildSha256, request.initialFen, request.moves,
        request.rootFen, request.nodes, request.multiPV, request.expectedPvCount, request.depth ?? null, request.searchMoves ?? null]);
}

function cacheable(result, request) {
    return result?.providerID === STOCKFISH_PROVIDER.id && result.buildSha256 === STOCKFISH_PROVIDER.buildSha256
        && result.searchFinished === true && result.completeBatch === true && result.rootFen === request.rootFen
        && result.position?.initialFen === request.initialFen && JSON.stringify(result.position?.moves) === JSON.stringify(request.moves)
        && result.options?.nodes === request.nodes && result.options?.limitStrength === false
        && result.lines?.length > 0 && result.lines.every(line => line.bound === 'EXACT' && line.depth === result.depth);
}

function post(owner, message) {
    if (owner.closed) return;
    try { owner.worker.postMessage(message); }
    catch {
        owner.closed = true;
        for (const job of owner.jobs.values()) job.controller.abort();
        owner.jobs.clear(); owners.delete(owner);
    }
}
function stop(owner, requestId, message = 'Stockfish consumer cancelled') {
    const job = owner.jobs.get(requestId);
    if (!job) return;
    owner.jobs.delete(requestId);
    job.controller.abort();
    post(owner, { kind: 'j7-stockfish-response', requestId, error: message });
}
function stopAll(owner) { for (const requestId of [...owner.jobs.keys()]) stop(owner, requestId); }

async function respond(owner, data) {
    const requestId = data.requestId;
    if (typeof requestId !== 'string' || !/^[A-Za-z0-9_-]{1,100}$/.test(requestId)) return;
    if (data.kind === 'j7-stockfish-cancel') return stop(owner, requestId);
    if (data.kind !== 'j7-stockfish-request') return;
    if (owner.jobs.has(requestId)) return stop(owner, requestId, 'Duplicate Stockfish request');
    let request;
    try { request = normalizeAnalysisRequest({ ...data.request, signal: undefined }); }
    catch (error) { return post(owner, { kind: 'j7-stockfish-response', requestId, error: String(error) }); }
    const client = owner.client ?? (sharedClient ??= createStockfishClient());
    clients.add(client);
    const cache = cacheFor(client);
    const cacheKey = key(request);
    const cached = cache.get(cacheKey);
    if (cached) {
        cache.delete(cacheKey); cache.set(cacheKey, cached);
        return post(owner, { kind: 'j7-stockfish-response', requestId,
            result: { ...clone(cached.result), cacheReuse: true, cacheOf: cached.queryId } });
    }
    const job = { controller: new AbortController(), queryId: 'sf-' + (++serial) };
    owner.jobs.set(requestId, job);
    try {
        const result = await client.analyze({ ...request, signal: job.controller.signal });
        if (owner.closed || owner.jobs.get(requestId) !== job) return;
        if (cacheable(result, request)) {
            cache.set(cacheKey, { result: clone(result), queryId: job.queryId });
            while (cache.size > 64) cache.delete(cache.keys().next().value);
        }
        post(owner, { kind: 'j7-stockfish-response', requestId, result: { ...result, cacheReuse: false, queryId: job.queryId } });
    } catch (error) {
        if (!owner.closed && owner.jobs.get(requestId) === job) post(owner, { kind: 'j7-stockfish-response', requestId, error: String(error) });
    } finally { if (owner.jobs.get(requestId) === job) owner.jobs.delete(requestId); }
}

/** A normal Java Worker interface; private analysis RPCs never reach UI listeners. */
export function createEngineWorker({ WorkerCtor = globalThis.Worker, client,
    url = new URL('./engine-worker.mjs', import.meta.url) } = {}) {
    const worker = new WorkerCtor(url, { type: 'module' });
    const owner = { worker, client, jobs: new Map(), closed: false };
    owners.add(owner);
    const wrappers = new Map();
    let onmessage = null;
    worker.addEventListener('message', event => { if (privateMessage(event.data)) void respond(owner, event.data).catch(error => {
        post(owner, { kind: 'j7-stockfish-response', requestId: event.data.requestId, error: String(error) });
    }); });
    worker.addEventListener('error', () => stopAll(owner));
    worker.addEventListener('messageerror', () => stopAll(owner));
    const terminate = () => {
        if (owner.closed) return;
        owner.closed = true;
        stopAll(owner); owners.delete(owner);
        worker.terminate();
    };
    const add = (type, listener, options) => {
        if (type !== 'message') return worker.addEventListener(type, listener, options);
        let registrations = wrappers.get(listener);
        if (!registrations) { registrations = new Map(); wrappers.set(listener, registrations); }
        const captured = capture(options);
        let wrapped = registrations.get(captured);
        if (!wrapped) {
            wrapped = event => {
                if (privateMessage(event.data)) return;
                if (typeof listener === 'function') listener.call(worker, event); else listener?.handleEvent(event);
            };
            registrations.set(captured, wrapped);
        }
        worker.addEventListener(type, wrapped, options);
    };
    const remove = (type, listener, options) => {
        if (type !== 'message') return worker.removeEventListener(type, listener, options);
        const registrations = wrappers.get(listener);
        const captured = capture(options);
        const wrapped = registrations?.get(captured);
        if (wrapped) { worker.removeEventListener(type, wrapped, options); registrations.delete(captured); }
        if (registrations?.size === 0) wrappers.delete(listener);
    };
    return new Proxy(worker, {
        get(target, property) {
            if (property === 'terminate') return terminate;
            if (property === 'addEventListener') return add;
            if (property === 'removeEventListener') return remove;
            if (property === 'onmessage') return onmessage;
            const value = Reflect.get(target, property, target);
            return typeof value === 'function' ? value.bind(target) : value;
        },
        set(target, property, value) {
            if (property === 'onmessage') {
                onmessage = value;
                target.onmessage = typeof value === 'function' ? event => { if (!privateMessage(event.data)) value.call(target, event); } : value;
                return true;
            }
            return Reflect.set(target, property, value, target);
        },
    });
}

export function disposeStockfishBroker() {
    for (const owner of owners) stopAll(owner);
    for (const client of clients) client.dispose?.();
    clients.clear(); caches = new WeakMap(); sharedClient = undefined;
}
