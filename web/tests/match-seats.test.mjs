import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { matchSeatLabels, renderMatchSeat } from '../src/main/web/match-seats.mjs';

const words = {
    levels: { BASIC: 'Basic', ADVANCED: 'Advanced', HARD: 'Hard' },
    'white-choice': 'White', 'black-choice': 'Black', toMove: 'to move',
};
const ai = { mode: 'AI', humanSide: 'WHITE', opponentName: 'Alex', level: 'BASIC', blackBelow: false,
    sideToMove: 'WHITE', result: 'IN_PROGRESS', ply: 8, viewPly: 8 };

test('AI name and level are adjacent to their captures across orientation and history', () => {
    const normal = matchSeatLabels(ai, 'Ana', words);
    assert.deepEqual([normal.top.text, normal.bottom.text], ['Alex · AI Basic', '●  Ana']);
    assert.deepEqual([normal.top.side, normal.bottom.side], ['BLACK', 'WHITE']);
    const flipped = matchSeatLabels({ ...ai, blackBelow: true, viewPly: 3, sideToMove: 'BLACK' }, 'Ana', words);
    assert.deepEqual([flipped.top.text, flipped.bottom.text], ['Ana', '●  Alex · AI Basic']);
    assert.deepEqual([flipped.top.side, flipped.bottom.side], ['WHITE', 'BLACK']);
    assert.equal(flipped.bottom.description, 'Alex · AI Basic · Black · to move');
    assert.equal(matchSeatLabels({ ...ai, result: 'WHITE_WINS', sideToMove: 'BLACK' }, 'Ana', words).top.active, false);
});

test('all three AI identities match native names without a browser or emulator', async () => {
    const core = await readFile(new URL('../../chess-core/src/main/java/ro/atf/chess/MatchOpponentName.java', import.meta.url), 'utf8');
    const native = await readFile(new URL('../../android/app/src/main/java/ro/atf/chess/android/MatchLabels.java', import.meta.url), 'utf8');
    const worker = await readFile(new URL('../src/main/java/ro/atf/chess/WebChessGame.java', import.meta.url), 'utf8');
    for (const [level, name] of [['BASIC', 'Alex'], ['ADVANCED', 'Mira'], ['HARD', 'Atlas']]) {
        assert.match(core, new RegExp(`case ${level} -> "${name}"`));
        assert.equal(matchSeatLabels({ ...ai, level, opponentName: name }, 'Ana', words).top.name,
            `${name} · AI ${words.levels[level]}`);
    }
    assert.match(native, /return MatchOpponentName\.forLevel\(level\)/);
    assert.match(worker, /"opponentName\\":.*MatchOpponentName\.forLevel/);
});

test('localized seat descriptions and long names retain a fixed board row', async () => {
    const ro = { levels: words.levels, 'white-choice': 'Alb', 'black-choice': 'Negru', toMove: 'la mutare' };
    const long = matchSeatLabels({ ...ai, humanSide: 'BLACK', level: 'HARD', opponentName: 'Atlas' }, 'Ștefania', ro);
    assert.equal(long.top.description, 'Ștefania · Negru');
    assert.equal(long.bottom.name, 'Atlas · AI Hard');
    const element = { dataset: {}, attributes: {}, setAttribute(key, value) { this.attributes[key] = value; } };
    renderMatchSeat(element, long.bottom);
    assert.equal(element.textContent, '●  Atlas · AI Hard');
    assert.equal(element.attributes.title, 'Atlas · AI Hard');
    const [html, css, layout] = await Promise.all([
        readFile(new URL('../src/main/web/index.html', import.meta.url), 'utf8'),
        readFile(new URL('../src/main/web/game-review.css', import.meta.url), 'utf8'),
        readFile(new URL('../src/main/web/board-layout.css', import.meta.url), 'utf8'),
    ]);
    assert.match(html, /match-player-top[^]*match-captures-top[^]*match-board[^]*match-player-bottom[^]*match-captures-bottom/);
    assert.match(css, /\.match-seat\s*\{[^}]*min-block-size:\s*var\(--board-seat-size\);[^}]*overflow:\s*hidden/);
    assert.match(css, /\.match-player\s*\{[^}]*max-inline-size:\s*48%;[^}]*text-overflow:\s*ellipsis;[^}]*white-space:\s*nowrap/);
    assert.match(css, /@media \(min-width: 481px\) and \(min-height: 481px\)\s*\{[^}]*--board-seat-size:\s*2\.1rem;/);
    assert.match(css, /\.match-seat\s*\{\s*display:\s*grid;\s*gap:\s*0;\s*align-content:\s*center;/);
    assert.match(css, /\.match\[data-mode="AI"\]:has\(\.match-inline-meter:not\(\[hidden\]\)\) > \.match-seat\s*\{[^}]*inline-size:\s*min\(100%, calc\(var\(--play-board-limit\) \+ var\(--board-meter-gutter\)\)\);[^}]*padding-inline-start:\s*var\(--board-meter-gutter\);/);
    assert.match(layout, /--board-reference-chrome:\s*calc\([^;]*2 \* var\(--board-seat-size\)/);
});
