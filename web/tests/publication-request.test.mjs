import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const script = new URL('../scripts/validate-publication-request.mjs', import.meta.url);
const request = new URL('../publication.request', import.meta.url);
const scriptPath = fileURLToPath(script);

test('the tracked request has one exact static commit and manifest identity', async () => {
    const result = spawnSync(process.execPath, [scriptPath, fileURLToPath(request)], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /^site_commit=[0-9a-f]{40}\nmanifest_sha256=[0-9a-f]{64}\n$/);
    const workflow = await readFile(new URL('../../.github/workflows/chess-pages.yml', import.meta.url), 'utf8');
    assert.match(workflow, /push:\s*\n\s*branches:\s*\[main\]\s*\n\s*paths:\s*\n\s*- web\/publication\.request/);
    assert.match(workflow, /node request-source\/web\/scripts\/validate-publication-request\.mjs/);
    assert.match(workflow, /steps\.request\.outputs\.site_commit/);
    assert.match(workflow, /steps\.request\.outputs\.manifest_sha256/);
});

test('malformed, duplicate, and substituted publication identities are rejected', async () => {
    const directory = await mkdtemp(path.join(tmpdir(), 'chess-pages-request-'));
    const file = path.join(directory, 'request');
    const valid = await readFile(request, 'utf8');
    try {
        for (const body of [
            valid.replace('SITE_COMMIT=', 'SITE_COMMIT=wrong'),
            valid.replace('MANIFEST_SHA256=', 'MANIFEST_SHA256=wrong'),
            valid + valid,
            valid.replace('\nMANIFEST_SHA256=', '\nEXTRA=1\nMANIFEST_SHA256='),
            valid.replace('MANIFEST_SHA256=', 'MANIFEST_SHA1='),
        ]) {
            await writeFile(file, body);
            const result = spawnSync(process.execPath, [scriptPath, file], { encoding: 'utf8' });
            assert.notEqual(result.status, 0, body);
            assert.equal(result.stdout, '');
        }
    } finally {
        assert.ok(directory.startsWith(path.resolve(tmpdir()) + path.sep));
        await rm(directory, { recursive: true, force: true });
    }
});
