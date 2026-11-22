import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { renderMatchCaptures } from '../src/main/web/match-captures.mjs';

class Element {
    children = [];
    attributes = new Map();
    dataset = {};
    textContent = '';
    className = '';
    constructor(tag, ownerDocument) { this.tag = tag; this.ownerDocument = ownerDocument; }
    setAttribute(name, value) { this.attributes.set(name, String(value)); }
    removeAttribute(name) { this.attributes.delete(name); }
    replaceChildren(...children) { this.children = children; }
    append(child) { this.children.push(child); }
}

function element(tag = 'div') {
    const document = { createElement: name => new Element(name, document),
        createElementNS: (_, name) => new Element(name, document) };
    return new Element(tag, document);
}

const words = {
    'white-choice': 'White', 'black-choice': 'Black',
    pieces: { PAWN: 'pawn', KNIGHT: 'knight', BISHOP: 'bishop', ROOK: 'rook', QUEEN: 'queen' },
    captured: (side, pieces, advantage) => side + ' captured ' + pieces + (advantage ? ' · Material +' + advantage : ''),
};
const artwork = {
    p: { body: 'M 0 0 L 10 10 Z', details: 'M 1 1 L 9 9' },
    n: { body: 'M 0 0 L 10 10 Z', details: 'M 1 1 L 9 9' },
    b: { body: 'M 0 0 L 10 10 Z', details: 'M 1 1 L 9 9' },
    r: { body: 'M 0 0 L 10 10 Z', details: 'M 1 1 L 9 9' },
    q: { body: 'M 0 0 L 10 10 Z', details: 'M 1 1 L 9 9' },
};

test('AI captures stay in their top and bottom trays through reply and history rendering', () => {
    const top = element();
    const bottom = element();
    const afterHumanCapture = {
        top: { side: 'BLACK', pieces: [], advantage: 0 },
        bottom: { side: 'WHITE', pieces: [{ kind: 'PAWN', count: 1 }], advantage: 1 },
    };
    const afterBotReply = {
        top: { side: 'BLACK', pieces: [{ kind: 'PAWN', count: 1 }], advantage: 0 },
        bottom: { side: 'WHITE', pieces: [{ kind: 'PAWN', count: 1 }], advantage: 0 },
    };
    assert.equal(renderMatchCaptures(top, afterHumanCapture.top, artwork, words), false);
    assert.equal(renderMatchCaptures(bottom, afterHumanCapture.bottom, artwork, words), true);
    assert.equal(bottom.attributes.get('aria-hidden'), 'false');
    assert.equal(bottom.attributes.get('aria-label'), 'White captured pawn · Material +1');
    assert.equal(bottom.children[0].dataset.shape, 'p');

    assert.equal(renderMatchCaptures(top, afterBotReply.top, artwork, words), true);
    assert.equal(top.attributes.get('aria-label'), 'Black captured pawn');
    assert.equal(top.children[0].dataset.shape, 'p');
    assert.equal(renderMatchCaptures(bottom, afterBotReply.bottom, artwork, words), true);
    assert.equal(bottom.children[0].dataset.shape, 'p');

    assert.equal(renderMatchCaptures(top, afterHumanCapture.top, artwork, words), false);
    assert.equal(top.attributes.get('aria-hidden'), 'true');
    assert.equal(top.children.length, 0);
    assert.equal(renderMatchCaptures(bottom, afterHumanCapture.bottom, artwork, words), true);
    assert.equal(bottom.attributes.get('aria-label'), 'White captured pawn · Material +1');
});

test('captured tray displays repeated pieces, side colors and clears stale frame data', () => {
    const tray = element();
    const seat = { side: 'BLACK', pieces: [
        { kind: 'PAWN', count: 2 }, { kind: 'QUEEN', count: 1 },
    ], advantage: 3 };
    assert.equal(renderMatchCaptures(tray, seat, artwork, words), true);
    assert.equal(tray.dataset.side, 'BLACK');
    assert.equal(tray.attributes.get('aria-label'), 'Black captured pawn × 2, queen · Material +3');
    const [pawn, count, queen, advantage] = tray.children;
    assert.equal(pawn.dataset.shape, 'p');
    assert.equal(pawn.className.includes('white'), true, 'Black captures use White piece art');
    assert.equal(count.textContent, '2');
    assert.equal(queen.dataset.shape, 'q');
    assert.equal(advantage.textContent, '+3');

    assert.equal(renderMatchCaptures(tray, { side: 'WHITE', pieces: [], advantage: 0 }, artwork, words), false);
    assert.equal(tray.dataset.side, 'WHITE');
    assert.equal(tray.attributes.get('aria-hidden'), 'true');
    assert.equal(tray.attributes.has('aria-label'), false);
    assert.equal(tray.children.length, 0);
});

test('every capturable kind uses the shared silhouette and the captured side color', () => {
    const tray = element();
    for (const [kind, shape] of [['PAWN', 'p'], ['KNIGHT', 'n'], ['BISHOP', 'b'], ['ROOK', 'r'], ['QUEEN', 'q']]) {
        assert.equal(renderMatchCaptures(tray, { side: 'BLACK', pieces: [{ kind, count: 1 }], advantage: 0 }, artwork, words), true);
        const [piece] = tray.children;
        const [svg] = piece.children;
        const [silhouette] = svg.children;
        assert.equal(piece.dataset.captured, kind);
        assert.equal(piece.dataset.shape, shape);
        assert.equal(piece.className.includes('white'), true, kind + ' captured by Black uses White artwork');
        assert.equal(silhouette.attributes.get('d'), artwork[shape].body);
        assert.equal(svg.children.length, 1, kind + ' has the same silhouette-only capture style as Android');
    }
    assert.equal(renderMatchCaptures(tray, { side: 'WHITE', pieces: [{ kind: 'KING', count: 1 }], advantage: 0 }, artwork, words), false);
    assert.equal(tray.children.length, 0, 'a king is never invented as a captured piece');
});

test('capture rows provide the same accessible contract in Romanian', () => {
    const tray = element();
    const romanian = {
        ...words,
        'white-choice': 'Alb',
        pieces: { PAWN: 'pionul', KNIGHT: 'calul', BISHOP: 'nebunul', ROOK: 'turnul', QUEEN: 'dama' },
        captured: (side, pieces, advantage) => side + ' a capturat ' + pieces + (advantage ? ' · Material +' + advantage : ''),
    };
    assert.equal(renderMatchCaptures(tray,
        { side: 'WHITE', pieces: [{ kind: 'PAWN', count: 1 }], advantage: 1 }, artwork, romanian), true);
    assert.equal(tray.attributes.get('aria-label'), 'Alb a capturat pionul · Material +1');
});

test('captured pieces match the APK silhouette, palette, and icon scale', async () => {
    const tray = element();
    const shape = 'M 12 39 C 12 34 17 31 19 28 Z';
    const native = await readFile(new URL('../../android/app/src/main/java/ro/atf/chess/android/MatchCapturesView.java', import.meta.url), 'utf8');
    const css = await readFile(new URL('../src/main/web/game-review.css', import.meta.url), 'utf8');
    assert.equal(renderMatchCaptures(tray, { side: 'BLACK', pieces: [{ kind: 'PAWN', count: 1 }], advantage: 0 },
        { p: { body: shape, details: 'M 1 1 L 9 9' } }, words), true);
    const [icon] = tray.children;
    const [svg] = icon.children;
    const [silhouette] = svg.children;
    assert.equal(svg.attributes.get('viewBox'), '0 0 48 48');
    assert.equal(silhouette.attributes.get('d'), shape);
    assert.equal(silhouette.attributes.has('class'), true);
    assert.equal(silhouette.attributes.get('class'), 'captured-piece-silhouette');
    assert.equal(svg.children.length, 1, 'captured icons omit inner detail strokes as on Android');

    assert.match(native, /Math\.round\(getTextSize\(\)\s*\*\s*\.82f\)/);
    assert.match(css, /\.match-captures\s*\{[^}]*font-size:\s*\.72rem/s);
    assert.match(css, /\.captured-piece\s*\{[^}]*inline-size:\s*\.59rem;[^}]*block-size:\s*\.59rem;/);
    for (const color of ['0xfff1efe5', '0xff65666d', '0xff5a5b56', '0xffc0bfb4']) {
        assert.ok(native.includes(color), `Android capture palette contains ${color}`);
        assert.ok(css.includes(color.slice(4).replace(/^/, '#').toLowerCase()), `Web capture palette contains ${color}`);
    }
    assert.match(css, /\.captured-piece\[data-shape\] \.captured-piece-silhouette\s*\{[^}]*fill:\s*#65666d;[^}]*stroke:\s*#c0bfb4;[^}]*stroke-width:\s*2/s);
    assert.match(css, /\.captured-piece\.white\[data-shape\] \.captured-piece-silhouette\s*\{[^}]*fill:\s*#f1efe5;[^}]*stroke:\s*#5a5b56/s);
});

test('match markup keeps the board between stable top and bottom capture rows', async () => {
    const [html, css] = await Promise.all([
        readFile(new URL('../src/main/web/index.html', import.meta.url), 'utf8'),
        readFile(new URL('../src/main/web/game-review.css', import.meta.url), 'utf8'),
    ]);
    const top = html.indexOf('id="match-captures-top"');
    const topName = html.indexOf('id="match-player-top"');
    const board = html.indexOf('id="match-board"');
    const bottomName = html.indexOf('id="match-player-bottom"');
    const bottom = html.indexOf('id="match-captures-bottom"');
    assert.ok(topName >= 0 && topName < top && top < board && board < bottomName && bottomName < bottom,
        'each capture tray follows its named seat without moving the board');
    assert.match(css, /\.match-seat\s*\{[^}]*min-block-size:\s*var\(--board-seat-size\)/);
    assert.match(css, /\.match-captures\[aria-hidden="true"\]\s*\{\s*visibility:\s*hidden/);
});

test('recorded APK capture frames render identically in Web history', async () => {
    const fixture = JSON.parse(await readFile(new URL('../../atf-tests/src/test/resources/match-capture-rows.json', import.meta.url), 'utf8'));
    const top = element();
    const bottom = element();
    for (const frame of fixture.frames) {
        for (const [row, tray] of [['top', top], ['bottom', bottom]]) {
            const expected = frame[row];
            const visible = renderMatchCaptures(tray, expected, artwork, words);
            assert.equal(visible, expected.pieces.length > 0 || expected.advantage > 0, `ply ${frame.ply} ${row} visibility`);
            assert.equal(tray.dataset.side, expected.side, `ply ${frame.ply} ${row} owner`);
            assert.deepEqual(tray.children.filter(child => child.dataset.captured).map(child => child.dataset.captured),
                expected.pieces.map(piece => piece.kind), `ply ${frame.ply} ${row} captured kinds`);
            assert.equal(tray.children.find(child => child.className === 'captured-piece-count')?.textContent,
                expected.pieces.find(piece => piece.count > 1)?.count?.toString(), `ply ${frame.ply} ${row} repeated pieces`);
            assert.equal(tray.children.find(child => child.className === 'match-material-advantage')?.textContent,
                expected.advantage > 0 ? `+${expected.advantage}` : undefined, `ply ${frame.ply} ${row} advantage`);
        }
    }
    const previous = fixture.frames.at(-2);
    renderMatchCaptures(top, previous.top, artwork, words);
    assert.equal(top.children.some(child => child.className === 'captured-piece-count'), false,
        'returning to the previous move removes the future second captured pawn');
});
