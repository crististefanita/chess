import { assert, test } from './support/atf-node.mjs';
import { readFile, readdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { verifyStockfishPackage } from '../tools/verify-stockfish.mjs';
import { analysisScore, renderAnalysisEvidence } from '../src/main/web/study-analysis.mjs';
import { stockfishLabel } from '../src/main/web/stockfish-label.mjs';

const files = new Map(await Promise.all((await readdir(new URL('../src/main/web/stockfish/', import.meta.url)))
    .map(async name => ['stockfish/' + name, await readFile(new URL('../src/main/web/stockfish/' + name, import.meta.url))])));
const copy = () => new Map(files);

test('The distribution pins the Lite upstream engine and carries corresponding source and GPL notices', () => {
    const result = verifyStockfishPackage(files);
    assert.equal(result.providerID, 'stockfish-19.0.1-lite-single');
    assert.equal(result.files.length, 4);
    assert.equal(result.files.find(file => file.path.endsWith('.wasm')).bytes, 1_845_971);
});

test('An edited asset and a matching self-authored manifest cannot replace the upstream binary', () => {
    const changed = copy(); const binary = Buffer.from(changed.get('stockfish/stockfish-19-lite-single.js')); binary[10] ^= 1;
    changed.set('stockfish/stockfish-19-lite-single.js', binary);
    const manifest = JSON.parse(changed.get('stockfish/manifest.json'));
    manifest.files.find(row => row.path.endsWith('.js')).sha256 = createHash('sha256').update(binary).digest('hex');
    changed.set('stockfish/manifest.json', Buffer.from(JSON.stringify(manifest)));
    assert.throws(() => verifyStockfishPackage(changed), /pinned upstream/);
});

test('Missing binary, licence and foreign source refuse publication', () => {
    for (const name of ['stockfish-19-lite-single.wasm', 'COPYING.txt', 'AUTHORS']) {
        const changed = copy(); changed.delete('stockfish/' + name);
        assert.throws(() => verifyStockfishPackage(changed), /Missing Stockfish asset/);
    }
    const changed = copy(); const manifest = JSON.parse(changed.get('stockfish/manifest.json')); manifest.sourceCommit = 'a'.repeat(40);
    changed.set('stockfish/manifest.json', Buffer.from(JSON.stringify(manifest)));
    assert.throws(() => verifyStockfishPackage(changed), /another engine or source/);
    for (const name of ['stockfish-19-single.js', 'stockfish-19-single.wasm', 'other-engine.bin']) {
        const extra = copy(); extra.set('stockfish/' + name, Buffer.from('unapproved engine bytes'));
        assert.throws(() => verifyStockfishPackage(extra), /only the pinned Lite distribution/);
    }
    const fullIdentity = copy(); const fullManifest = JSON.parse(fullIdentity.get('stockfish/manifest.json'));
    fullManifest.providerID = 'stockfish-19.0.1-full-single';
    fullIdentity.set('stockfish/manifest.json', Buffer.from(JSON.stringify(fullManifest)));
    assert.throws(() => verifyStockfishPackage(fullIdentity), /another engine or source/);
});

test('Coaching names the engine and normalizes a Black PV without manufacturing replay proof', () => {
    const engine = { providerID: 'stockfish-19.0.1-lite-single' };
    assert.match(analysisScore({ engine, ending: 'HORIZON', centipawns: -125 }, 'en'), /Stockfish 19 Lite.*−1.25.*Black/);
    const target = { textContent: '' };
    renderAnalysisEvidence(target, { kind: 'ENGINE', engine, depth: 14, centipawns: 125, mate: null, rootSide: 'BLACK' }, 'WHITE', 'en', () => { throw new Error('No invented proof button'); });
    assert.equal(target.textContent, 'Stockfish 19 Lite · Depth 14 plies · −1.25');
    renderAnalysisEvidence(target, { kind: 'ENGINE', depth: 16, centipawns: null, mate: 3, rootSide: 'BLACK' }, 'WHITE', 'en', () => {});
    assert.match(target.textContent, /Mate in 3.*Black/);
    assert.doesNotMatch(target.textContent, /NaN|undefined/);
    const archivedFull = Object.freeze({ providerID: 'stockfish-19.0.1-full-single' });
    assert.equal(stockfishLabel(archivedFull), 'Stockfish 19 Full');
    assert.equal(archivedFull.providerID, 'stockfish-19.0.1-full-single');
    assert.match(analysisScore({ engine: archivedFull, ending: 'HORIZON', centipawns: 0 }, 'en'), /^Stockfish 19 Full/);
});

test('Analysis lightbulb stays muted while idle and uses a visible active color only after disclosure', async () => {
    const styles = await readFile(new URL('../src/main/web/study-analysis.css', import.meta.url), 'utf8');
    assert.match(styles, /\.analysis-study-hint-button\s*\{\s*color:\s*#a8a69e\s*!important;\s*\}/);
    assert.match(styles, /\.analysis-study-hint-button\[aria-pressed="true"\]\s*\{\s*color:\s*#57e0b4\s*!important;\s*\}/);
    assert.match(styles, /\.analysis-study-hint-button svg\s*\{[^}]*fill:\s*none;[^}]*stroke:\s*currentColor;/);
});
