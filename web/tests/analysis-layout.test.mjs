import { assert, test } from './support/atf-node.mjs';
import { readFile } from 'node:fs/promises';
const css = await readFile(new URL('../src/main/web/study-analysis.css', import.meta.url), 'utf8');

test('The manual board stage has intrinsic height instead of pushing all feedback to the viewport bottom', () => {
    const stage = css.match(/\.analysis-study-stage\s*\{([^}]+)\}/)?.[1];
    assert.ok(stage);
    assert.match(stage, /flex:\s*0 0 auto/);
    assert.match(stage, /min-block-size:\s*0/);
    assert.match(stage, /place-items:\s*start center/);
    assert.doesNotMatch(stage, /(?:flex:\s*1|min-block-size:\s*var\(--board-stage-min\))/);
});

test('Feedback reserves the hint row while position and navigation remain adjacent to the board', () => {
    assert.match(css, /\.analysis-study-status\s*\{[^}]*min-block-size:\s*1\.25rem/);
    assert.match(css, /\.analysis-study-feedback\s*\{[^}]*min-block-size:\s*var\(--board-feedback-size\)/);
    assert.match(css, /\.analysis-study-stage\s*\{[^}]*margin-block:\s*\.25rem/);
    assert.doesNotMatch(css, /\.analysis-study-(?:feedback|toolbar|status)\s*\{[^}]*(?:margin-(?:block-start|top):\s*auto|position:\s*(?:fixed|absolute))/);
});
