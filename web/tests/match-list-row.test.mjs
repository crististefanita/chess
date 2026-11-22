import assert from 'node:assert/strict';
import { test } from 'node:test';
import { matchListOpponent } from '../src/main/web/match-list-row.mjs';

test('Saved and finished rows render every AI level and the local opponent in both languages', () => {
    for (const labels of [
        { 'open-match': 'Local game', levels: { BASIC: 'Basic', ADVANCED: 'Advanced', HARD: 'Hard' } },
        { 'open-match': 'Partidă locală', levels: { BASIC: 'Basic', ADVANCED: 'Advanced', HARD: 'Hard' } },
    ]) {
        assert.equal(matchListOpponent({ mode: 'LOCAL' }, labels), labels['open-match']);
        for (const level of ['BASIC', 'ADVANCED', 'HARD']) {
            assert.equal(matchListOpponent({ mode: 'AI', level }, labels), `AI · ${labels.levels[level]}`);
        }
    }
});

test('A malformed saved AI level fails explicitly instead of leaving a stale menu visible', () => {
    const labels = { 'open-match': 'Local game', levels: { BASIC: 'Basic' } };
    assert.throws(() => matchListOpponent({ mode: 'AI', level: 'UNKNOWN' }, labels), /Unsupported AI level/);
});
