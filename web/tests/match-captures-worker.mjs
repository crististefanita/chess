import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const [worker, fixturePath] = process.argv.slice(2);
assert.ok(worker && fixturePath, 'Supply the packaged worker and shared capture frames');
const fixture = JSON.parse(readFileSync(fixturePath, 'utf8'));
let nextId = 0;
let reply;
globalThis.self = { postMessage: message => { if (!Object.hasOwn(message, 'progress')) reply(message); } };
await import(pathToFileURL(worker));

async function request(type, data = {}) {
    const id = ++nextId;
    const result = await new Promise(resolve => {
        reply = resolve;
        self.onmessage({ data: { id, type, ...data } });
    });
    assert.equal(result.id, id);
    assert.equal(result.error, undefined, result.error);
    return result;
}

let state = await request('ledger-open', { ledger: null, legacy: {} });
state = await request('match-open', { setup: { mode: 'AI', humanSide: 'WHITE', level: 'BASIC', analysis: false } });
assert.equal(typeof state.match.opponentName, 'string');
assert.ok(state.match.opponentName.length > 0, 'a new AI match records an opponent identity');
const opponentName = state.match.opponentName;
console.log(`opponent-name\t${state.match.opponentName}`);
for (let index = 0; index < fixture.moves.length; index++) {
    const type = index % 2 === 0 ? 'match-play' : 'match-bot-apply';
    state = await request(type, { expectedRevision: state.match.revision, move: fixture.moves[index] });
    assert.equal(state.outcome, 'APPLIED', `${type} ${fixture.moves[index]}`);
}
for (const frame of fixture.frames) {
    state = await request('match-history', { expectedRevision: state.match.revision, ply: frame.ply });
    assert.deepEqual(state.match.captures, { top: frame.top, bottom: frame.bottom }, `captures at ply ${frame.ply}`);
    console.log(`${frame.ply}\t${JSON.stringify(state.match.captures)}`);
}
state = await request('match-flip', { expectedRevision: state.match.revision });
const last = fixture.frames.at(-1);
assert.deepEqual(state.match.captures, { top: last.bottom, bottom: last.top });
console.log(`flipped\t${JSON.stringify(state.match.captures)}`);
const saved = state.ledger;
await request('ledger-open', { ledger: saved });
state = await request('match-open', { route: 'AI' });
assert.deepEqual(state.match.captures, { top: last.bottom, bottom: last.top });
assert.equal(state.match.opponentName, opponentName, 'the selected AI identity survives reload');
console.log(`restored\t${JSON.stringify(state.match.captures)}`);
