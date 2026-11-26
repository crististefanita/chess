import { createHash } from 'node:crypto';

export const pinnedStockfish = Object.freeze({
    providerID: 'stockfish-19.0.1-lite-single', version: '19.0.1',
    sourceCommit: '0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6',
    assets: Object.freeze({
        'stockfish-19-lite-single.js': { bytes: 28_230, sha256: 'ccb130d7ff29408531c47d859a49f91d59d4f482d05a53eb1a3231a4f624c91a' },
        'stockfish-19-lite-single.wasm': { bytes: 1_845_971, sha256: 'ee4f115bdb2d32cadf96cada104a05b5ec257d7ad6f57582190b49bc71f096de' },
    }),
});

const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');

/** Build-time check against the independently pinned release, not just a self-authored manifest. */
export function verifyStockfishPackage(files) {
    const allowed = new Set([...Object.keys(pinnedStockfish.assets), 'manifest.json', 'README.md', 'COPYING.txt', 'AUTHORS']);
    for (const name of files.keys()) {
        if (name.startsWith('stockfish/') && !allowed.has(name.slice('stockfish/'.length))) {
            throw new Error('Unexpected Stockfish asset; only the pinned Lite distribution may be published: ' + name);
        }
    }
    const asset = name => {
        const value = files.get('stockfish/' + name);
        if (!Buffer.isBuffer(value)) throw new Error('Missing Stockfish asset: ' + name);
        return value;
    };
    const manifest = JSON.parse(asset('manifest.json').toString('utf8'));
    if (manifest.schema !== 1 || manifest.providerID !== pinnedStockfish.providerID
        || manifest.version !== pinnedStockfish.version || manifest.sourceCommit !== pinnedStockfish.sourceCommit
        || !Array.isArray(manifest.files)) throw new Error('Stockfish manifest names another engine or source');
    const entries = new Map();
    for (const entry of manifest.files) {
        if (!entry || typeof entry.path !== 'string' || entry.path.includes('/') || entry.path.includes('\\')
            || entries.has(entry.path)) throw new Error('Invalid or duplicate Stockfish manifest entry');
        entries.set(entry.path, entry);
    }
    if (entries.size !== Object.keys(pinnedStockfish.assets).length + 2
        || [...entries.keys()].some(name => !pinnedStockfish.assets[name] && name !== 'COPYING.txt' && name !== 'AUTHORS')) {
        throw new Error('Unexpected Stockfish manifest asset');
    }
    const checked = [];
    for (const name of [...Object.keys(pinnedStockfish.assets), 'COPYING.txt', 'AUTHORS']) {
        const bytes = asset(name);
        const entry = entries.get(name);
        const observed = { path: name, bytes: bytes.length, sha256: sha256(bytes) };
        if (!entry || entry.bytes !== observed.bytes || entry.sha256 !== observed.sha256) {
            throw new Error('Stockfish asset differs from its manifest: ' + name);
        }
        const pinned = pinnedStockfish.assets[name];
        if (pinned && (pinned.bytes !== observed.bytes || pinned.sha256 !== observed.sha256)) {
            throw new Error('Stockfish asset differs from the pinned upstream release: ' + name);
        }
        checked.push(observed);
    }
    const licence = asset('COPYING.txt').toString('utf8');
    if (licence.length < 30_000 || !licence.includes('GNU GENERAL PUBLIC LICENSE')
        || !licence.includes('Version 3, 29 June 2007') || asset('AUTHORS').length < 1000) {
        throw new Error('Stockfish distribution notices are incomplete');
    }
    const sourceNotice = asset('README.md').toString('utf8');
    if (!sourceNotice.includes(pinnedStockfish.sourceCommit) || !sourceNotice.includes('GPL')) {
        throw new Error('Stockfish corresponding-source notice is missing');
    }
    return { providerID: pinnedStockfish.providerID, sourceCommit: pinnedStockfish.sourceCommit, files: checked };
}
