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

test('match markup keeps the board between stable top and bottom capture rows', async () => {
    const [html, css] = await Promise.all([
        readFile(new URL('../src/main/web/index.html', import.meta.url), 'utf8'),
        readFile(new URL('../src/main/web/game-review.css', import.meta.url), 'utf8'),
    ]);
    const top = html.indexOf('id="match-captures-top"');
    const board = html.indexOf('id="match-board"');
    const bottom = html.indexOf('id="match-captures-bottom"');
    assert.ok(top >= 0 && top < board && board < bottom, 'top tray, board and lower tray remain in visual order');
    assert.match(css, /\.match-captures\s*\{[^}]*min-block-size:\s*var\(--board-seat-size\)/);
    assert.match(css, /\.match-captures\[aria-hidden="true"\]\s*\{\s*visibility:\s*hidden/);
});