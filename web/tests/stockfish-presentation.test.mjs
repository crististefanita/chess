import { assert, test } from './support/atf-node.mjs';
import { readFile, readdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { verifyStockfishPackage } from '../tools/verify-stockfish.mjs';
import { analysisScore, renderAnalysisEvidence } from '../src/main/web/study-analysis.mjs';

const files = new Map(await Promise.all((await readdir(new URL('../src/main/web/stockfish/', import.meta.url)))
    .map(async name => ['stockfish/' + name, await readFile(new URL('../src/main/web/stockfish/' + name, import.meta.url))])));
const copy = () => new Map(files);

test('The distribution pins the full upstream engine and carries corresponding source and GPL notices', () => {
    const result = verifyStockfishPackage(files);
    assert.equal(result.providerID, 'stockfish-19.0.1-full-single');
    assert.equal(result.files.length, 4);
});

test('An edited asset and a matching self-authored manifest cannot replace the upstream binary', () => {
    const changed = copy(); const binary = Buffer.from(changed.get('stockfish/stockfish-19-single.js')); binary[10] ^= 1;
    changed.set('stockfish/stockfish-19-single.js', binary);
    const manifest = JSON.parse(changed.get('stockfish/manifest.json'));
    manifest.files.find(row => row.path.endsWith('.js')).sha256 = createHash('sha256').update(binary).digest('hex');
    changed.set('stockfish/manifest.json', Buffer.from(JSON.stringify(manifest)));
    assert.throws(() => verifyStockfishPackage(changed), /pinned upstream/);
});

test('Missing binary, licence and foreign source refuse publication', () => {
    for (const name of ['stockfish-19-single.wasm', 'COPYING.txt', 'AUTHORS']) {
        const changed = copy(); changed.delete('stockfish/' + name);
        assert.throws(() => verifyStockfishPackage(changed), /Missing Stockfish asset/);
    }
    const changed = copy(); const manifest = JSON.parse(changed.get('stockfish/manifest.json')); manifest.sourceCommit = 'a'.repeat(40);
    changed.set('stockfish/manifest.json', Buffer.from(JSON.stringify(manifest)));
    assert.throws(() => verifyStockfishPackage(changed), /another engine or source/);
});

test('Coaching names the engine and normalizes a Black PV without manufacturing replay proof', () => {
    const engine = { providerID: 'stockfish-19.0.1-full-single' };
    assert.match(analysisScore({ engine, ending: 'HORIZON', centipawns: -125 }, 'en'), /Stockfish 19.*−1.25.*Black/);
    const target = { textContent: '' };
    renderAnalysisEvidence(target, { kind: 'ENGINE', depth: 14, centipawns: 125, mate: null, rootSide: 'BLACK' }, 'WHITE', 'en', () => { throw new Error('No invented proof button'); });
    assert.equal(target.textContent, 'Stockfish 19 · Depth 14 plies · −1.25');
    renderAnalysisEvidence(target, { kind: 'ENGINE', depth: 16, centipawns: null, mate: 3, rootSide: 'BLACK' }, 'WHITE', 'en', () => {});
    assert.match(target.textContent, /Mate in 3.*Black/);
    assert.doesNotMatch(target.textContent, /NaN|undefined/);
});
