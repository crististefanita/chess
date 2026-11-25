import { createHash } from 'node:crypto';

export const pinnedStockfish = Object.freeze({
    providerID: 'stockfish-19.0.1-full-single', version: '19.0.1',
    sourceCommit: '0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6',
    assets: Object.freeze({
        'stockfish-19-single.js': { bytes: 28_130, sha256: '8071dee87312f74805a859de933e1a1ef465579ab1a51fbd597388cdce9d444d' },
        'stockfish-19-single.wasm': { bytes: 99_169_054, sha256: '991bede2f253ebb08ff01fcc48270f4b9c8e66b12d5bd5f8d6f52eb3e3b3a740' },
    }),
});

const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');

/** Build-time check against the independently pinned release, not just a self-authored manifest. */
export function verifyStockfishPackage(files) {
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
