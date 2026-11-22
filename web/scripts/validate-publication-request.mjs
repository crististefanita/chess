import { readFileSync } from 'node:fs';

const file = process.argv[2];
if (!file) throw new Error('Supply the qualified publication request');
const content = readFileSync(file, 'utf8');
const request = /^SITE_COMMIT=([0-9a-f]{40})\r?\nMANIFEST_SHA256=([0-9a-f]{64})\r?\n?$/.exec(content);
if (!request) throw new Error('Publication request must contain exactly one site commit and manifest SHA-256');
process.stdout.write(`site_commit=${request[1]}\nmanifest_sha256=${request[2]}\n`);
