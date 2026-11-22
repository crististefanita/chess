import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { matchReviewActionState, reviewGradeTone } from '../src/main/web/review-presentation.mjs';

test('Every review grade keeps text and receives the APK color group', async () => {
    const positive = ['BRILLIANT', 'GREAT', 'BEST'];
    const warning = ['MISTAKE', 'MISS', 'BLUNDER'];
    const neutral = ['BOOK', 'FORCED', 'EXCELLENT', 'GOOD', 'INACCURACY'];
    for (const grade of positive) assert.equal(reviewGradeTone(grade), 'positive', grade);
    for (const grade of warning) assert.equal(reviewGradeTone(grade), 'warning', grade);
    for (const grade of neutral) assert.equal(reviewGradeTone(grade), 'neutral', grade);

    const [css, source] = await Promise.all([
        readFile(new URL('../src/main/web/game-review.css', import.meta.url), 'utf8'),
        readFile(new URL('../src/main/web/game-review.mjs', import.meta.url), 'utf8'),
    ]);
    assert.match(css, /\.history button\[data-grade-tone="positive"\]\s*\{\s*color:\s*var\(--review-accent\)/);
    assert.match(css, /\.history button\[data-grade-tone="warning"\]\s*\{\s*color:\s*#ffb591/);
    assert.match(source, /item\.dataset\.grade = grade;[\s\S]*?item\.dataset\.gradeTone = reviewGradeTone\(grade\)/);
    assert.match(source, /item\.textContent = reviewWorkspace\.moveLabel\(state, ply\)/);
});

test('Finished review action is terminal-only and uses the shared accessible route', async () => {
    const available = { result: 'WHITE_WINS', menu: { GAME_REVIEW: true } };
    assert.deepEqual(matchReviewActionState(available), { visible: true, disabled: false });
    assert.equal(matchReviewActionState(available, { browsing: true }).visible, false);
    assert.equal(matchReviewActionState({ ...available, result: 'IN_PROGRESS' }).visible, false);
    assert.equal(matchReviewActionState({ ...available, result: null }).visible, false);
    assert.equal(matchReviewActionState({ ...available, result: 'FUTURE_UNKNOWN_RESULT' }).visible, false);
    assert.equal(matchReviewActionState({ ...available, menu: { GAME_REVIEW: 'true' } }).visible, false);
    assert.equal(matchReviewActionState({ ...available, menu: {} }).visible, false);
    for (const blocker of ['running', 'engineFailed', 'otherTabChanged']) {
        assert.equal(matchReviewActionState(available, { [blocker]: true }).disabled, true, blocker);
    }

    const [markup, source] = await Promise.all([
        readFile(new URL('../src/main/web/index.html', import.meta.url), 'utf8'),
        readFile(new URL('../src/main/web/game-review.mjs', import.meta.url), 'utf8'),
    ]);
    const caption = markup.match(/<div class="match-caption">\s*<p id="match-turn"[^]*?<\/p>/)?.[0] ?? '';
    assert.match(caption, /id="match-review-result"[^>]*hidden/);
    assert.match(caption, /aria-hidden="true">📊<\/span>/);
    assert.equal((markup.match(/id="match-review-result"/g) ?? []).length, 1);
    assert.match(source, /reviewResult\.hidden = !matchReviewActionState\(match, \{ browsing \}\)\.visible/);
    assert.match(source, /find\('match-review-result'\)\.addEventListener\('click', \(\) => analyzeMatch\(true\)\)/);
    assert.match(source, /reviewResult\.setAttribute\('aria-label', words\['menu-review'\]\)/);
});
