import assert from 'node:assert/strict';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { runtimeDirectory } from '../tools/package-web.mjs';

const site = process.argv[2];
assert.ok(site, 'Provide the external Web build');
let receive;
globalThis.self = { postMessage: reply => receive(reply) };
await import(pathToFileURL(join(await runtimeDirectory(site), 'engine-worker.mjs')));

async function call(type, fields = {}) {
    const reply = await new Promise(resolve => {
        receive = resolve;
        self.onmessage({ data: { id: type, type, ...fields } });
    });
    assert.equal(reply.error, undefined, reply.error);
    return reply;
}

const start = (level = 'BASIC') => call('match-open', { setup: { mode: 'AI', humanSide: 'WHITE', level, analysis: false } });
await call('ledger-open');
const first = await start();
const replacedId = first.saved[0].id;
const firstName = first.match.opponentName;
assert.equal(first.saved[0].opponentName, firstName, 'the Saved row displays the new match identity');
let second = await start();
const secondName = second.match.opponentName;
assert.notEqual(secondName, firstName, 'concurrent games at the same AI level receive distinct names');
assert.equal(second.saved[0].opponentName, secondName, 'the second Saved row retains its own identity');
let refreshed = await call('ledger-open', { ledger: second.ledger });
assert.deepEqual(refreshed.saved, second.saved, 'browser refresh retains both saved identities');
second = await call('match-open', { route: 'AI' });
assert.equal(second.match.opponentName, secondName, 'resuming after refresh restores the selected game identity');
assert.equal(second.saved[1].opponentName, firstName, 'the other same-level game keeps its independent identity');
const advanced = await start('ADVANCED');
const hard = await start('HARD');
const randomNames = [firstName, secondName, advanced.match.opponentName, hard.match.opponentName];
assert.equal(new Set(randomNames).size, randomNames.length, 'Basic, Advanced, and Hard draw distinct names across active AI matches');
assert.ok(randomNames.every(name => typeof name === 'string' && name.length > 0));
let refreshedAll = await call('ledger-open', { ledger: hard.ledger });
assert.deepEqual(refreshedAll.saved.slice(0, 4).map(row => row.opponentName), [
    hard.match.opponentName, advanced.match.opponentName, secondName, firstName,
], 'all three levels and both same-level matches retain their names after browser refresh');
let state = hard;
for (let count = 5; count <= 10; count++) state = await start();
assert.equal(state.saved.length, 10);
assert.equal(state.finished.length, 0);
state = await start();
assert.equal(state.saved.length, 10);
assert.equal(state.saved.some(row => row.id === replacedId), false, 'the eleventh active game replaces the oldest');

let oldestResult;
for (let count = 1; count <= 11; count++) {
    state = await call('match-play', { expectedRevision: state.match.revision, move: 'e2e4' });
    state = await call('match-decision', { expectedRevision: state.match.revision, action: 'RESIGN' });
    assert.equal(state.outcome, 'APPLIED');
    assert.equal(state.saved.length, 9, 'a completed game leaves Saved in the same transaction');
    assert.equal(state.finished.length, Math.min(count, 10));
    if (!oldestResult) oldestResult = state.finished[0].id;
    state = await start();
}
assert.equal(state.saved.length, 10);
assert.equal(state.finished.length, 10);
assert.equal(state.finished.some(row => row.id === oldestResult), false, 'the eleventh result evicts only the oldest');
assert.equal(state.saved.every(row => row.result === 'IN_PROGRESS'), true);
assert.equal(state.finished.every(row => row.result !== 'IN_PROGRESS'), true);
assert.equal(state.saved.every(row => typeof row.opponentName === 'string' && row.opponentName.length > 0), true);
assert.equal(state.saved[0].opponentName, state.match.opponentName, 'the newest Saved row matches the active match');
const restored = await call('ledger-open', { ledger: state.ledger });
assert.deepEqual(restored.saved, state.saved);
assert.deepEqual(restored.finished, state.finished);
const resumed = await call('match-open', { route: 'AI' });
assert.equal(resumed.match.opponentName, state.saved[0].opponentName, 'resuming the newest Saved row keeps its name');
console.log('PASS Ten Saved and ten Finished rows remain separate after replacement and worker reload');
