import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { createOpeningFlow, createOpeningStudy, openingComparisonJump, openingControls, pickOpeningTap } from '../src/main/web/study-openings.mjs';

const tick = () => new Promise(resolve => setImmediate(resolve));

function fixture(responses, stored = null) {
  const calls = [];
  const checkpoints = [];
  const rendered = [];
  const errors = [];
  const timers = new Map();
  let timer = 0;
  const flow = createOpeningFlow({
    locale: 'ro',
    async call(action, fields) {
      calls.push({ action, ...fields });
      const answer = responses[action];
      if (answer instanceof Error) throw answer;
      if (typeof answer === 'function') return answer(fields);
      return answer;
    },
    read: () => stored,
    save: saved => { checkpoints.push(saved); stored = saved; },
    change: (_, state) => rendered.push(state),
    fail: error => errors.push(error),
    wait: (task, delay) => { timers.set(++timer, { task, delay }); return timer; },
    cancel: id => timers.delete(id),
  });
  return { flow, calls, checkpoints, rendered, errors, timers };
}

test('A learner move schedules exactly one native book reply with its saved position', async () => {
  const pending = { saved: 'lesson:white:move', canAutoReply: true };
  const done = { saved: 'lesson:white:reply', canAutoReply: false };
  const { flow, calls, checkpoints, timers, rendered } = fixture({
    CATALOG: { entries: [{ id: 'italian' }] },
    START: { saved: 'lesson:white:start', canAutoReply: false },
    PLAY: pending, REPLY: done,
  });
  await flow.open();
  await flow.dispatch('START', 'italian', 0);
  await flow.dispatch('PLAY', 'e2e4');
  assert.equal(timers.size, 1);
  const [[id, reply]] = timers;
  assert.equal(reply.delay, 450);
  timers.delete(id);
  reply.task();
  await tick();
  assert.deepEqual(calls.at(-1), { action: 'REPLY', value: '', index: 0, saved: pending.saved, option: 'ro' });
  assert.deepEqual(checkpoints, ['lesson:white:start', pending.saved, done.saved]);
  assert.equal(rendered.at(-1), done);
  assert.equal(timers.size, 0);
});

test('History review and route changes cancel delayed replies without moving the visible board', async () => {
  let establish;
  const { flow, rendered, timers, calls, checkpoints } = fixture({
    CATALOG: { entries: [] },
    RESTORE: { saved: 'black:pending', canAutoReply: true },
    SEEK: { saved: 'black:history', canAutoReply: false },
    FORWARD: () => new Promise(resolve => { establish = resolve; }),
  }, 'black:pending');
  await flow.open();
  flow.visibility(false);
  assert.equal(timers.size, 0);
  flow.visibility(true);
  assert.equal(timers.size, 1);
  await flow.dispatch('SEEK', '', 0);
  assert.equal(timers.size, 0);
  assert.equal(calls.at(-1).saved, 'black:pending');
  const late = flow.dispatch('FORWARD');
  flow.close();
  establish({ saved: 'must-not-overwrite', canAutoReply: true });
  await late;
  assert.equal(rendered.at(-1).saved, 'black:history');
  assert.deepEqual(checkpoints, ['black:history']);
  assert.equal(timers.size, 0);
});

test('An invalid opening checkpoint returns to the catalog without replaying a move', async () => {
  const { flow, checkpoints, rendered, errors, calls } = fixture({
    CATALOG: { entries: [] }, RESTORE: new Error('Invalid checkpoint'),
  }, 'corrupt');
  await flow.open();
  assert.deepEqual(checkpoints, ['']);
  assert.deepEqual(rendered, [null]);
  assert.deepEqual(errors, []);
  assert.deepEqual(calls.map(item => item.action), ['CATALOG', 'RESTORE']);
});

test('Only the taught move can be played by clicking a revealed destination directly', () => {
  const view = { canPlay: true, nextStep: { move: 'e2e4' }, hint: 'PIECE', legal: ['e2e4', 'e2e3', 'g1f3'] };
  assert.deepEqual(pickOpeningTap(view, null, 'f3', 'g1f3'), { selected: null, moves: [] });
  assert.deepEqual(pickOpeningTap(view, null, 'g1'), { selected: 'g1', moves: [] });
  assert.deepEqual(pickOpeningTap(view, 'g1', 'f3'), { selected: 'g1', moves: ['g1f3'] });
  assert.deepEqual(pickOpeningTap(view, null, 'e4', 'e2e4'), { selected: null, moves: ['e2e4'] });
  assert.deepEqual(pickOpeningTap({ ...view, hint: 'SOLUTION' }, null, 'e4'), { selected: null, moves: ['e2e4'] });
  assert.deepEqual(pickOpeningTap({ ...view, canPlay: false }, null, 'e4', 'e2e4'), { selected: null, moves: [] });
});

test('Lesson controls follow native boundaries, pause, review and completion', () => {
  const start = { ply: 0, practicePly: 0, total: 10, phase: 'LEARNER', canPlay: true };
  assert.deepEqual(openingControls(start), { previous: false, next: true, history: false, action: 'THEORY', actionEnabled: true });
  const replying = { ...start, ply: 1, practicePly: 1, phase: 'REPLY', canPlay: false };
  assert.deepEqual(openingControls(replying), { previous: true, next: true, history: true, action: 'THEORY', actionEnabled: false });
  assert.equal(openingControls({ ...replying, replyPaused: true }).action, 'RESUME');
  assert.equal(openingControls({ ...replying, replyPaused: true, reviewing: true }).action, 'REPLAY');
  const complete = { ...start, ply: 10, practicePly: 10, phase: 'COMPLETE', canPlay: false };
  assert.deepEqual(openingControls(complete), { previous: true, next: false, history: true, action: 'RESTART', actionEnabled: true });
});

test('A decisive comparison reaches the exact verified branch and ply', () => {
  const variants = [
    { move: 'e2e4', proof: { addedDefense: false }, frames: [{ lastMove: null }, { lastMove: 'e2e4' }, { lastMove: 'e7e5' }] },
    { move: 'd2d4', proof: { addedDefense: false }, frames: [{ lastMove: null }, { lastMove: 'd2d4' }, { lastMove: 'd7d5' }] },
  ];
  const proof = { branch: 'd2d4', ply: 2, move: 'd7d5' };
  assert.deepEqual(openingComparisonJump(variants, proof), { branch: 1, ply: 2 });
  assert.equal(openingComparisonJump(variants, { ...proof, move: 'e7e5' }), null);
  assert.equal(openingComparisonJump(variants, { ...proof, ply: 0 }), null);
  assert.equal(openingComparisonJump(variants, { ...proof, ply: 3 }), null);
  assert.equal(openingComparisonJump(variants, { ...proof, branch: 'g1f3' }), null);
  assert.equal(openingComparisonJump([variants[0], { ...variants[1], proof: { addedDefense: true } }], proof), null);
});

const longPlan = 'Develop the knight with tempo and retain control of the centre.\n'.repeat(7).trim();

async function mountLesson(locale, responses = {}) {
  const document = { addEventListener() {}, removeEventListener() {} };
  class Element {
    children = [];
    attributes = new Map();
    dataset = {};
    events = new Map();
    classList = { add() {}, toggle() {} };
    style = { setProperty() {} };
    isConnected = true;
    open = false;
    #text = '';
    textWrites = 0;
    constructor(tag) { this.tag = tag; }
    get textContent() { return this.#text; }
    set textContent(value) { this.#text = value; this.textWrites++; }
    append(...children) { this.children.push(...children); }
    replaceChildren(...children) { this.children = children; }
    setAttribute(name, value) { this.attributes.set(name, value); }
    toggleAttribute(name, flag) { if (flag) this.attributes.set(name, ''); else this.attributes.delete(name); }
    addEventListener(name, callback) { this.events.set(name, callback); }
    click() { this.events.get('click')(); }
    focus() { document.activeElement = this; }
    showModal() { this.open = true; this.children.at(-1).focus(); }
    close() { this.open = false; this.events.get('close')(); }
  }
  document.createElement = tag => new Element(tag);
  document.body = new Element('body');
  document.body.focus();
  const root = new Element('section');
  root.ownerDocument = document;
  function find(className, parent = root) {
    if (parent.className === className) return parent;
    for (const child of parent.children) {
      const found = find(className, child);
      if (found) return found;
    }
  }
  const lesson = { id: 'italian', title: 'Italian Game', ply: 0, practicePly: 0, total: 4,
    phase: 'LEARNER', canPlay: true, learner: 'WHITE', canAutoReply: false, sideToMove: 'WHITE',
    fen: '8/8/8/8/8/8/4P3/8', plan: longPlan, hint: 'NONE', legal: ['e2e3', 'e2e4'], nextStep: { move: 'e2e4', idea: 'Control d5.' } };
  let tap;
  let hint = 'NONE';
  let state = lesson;
  const calls = [];
  const frames = [];
  const study = createOpeningStudy({
    root, locale, home() {},
    board: (_, onTap) => { tap = onTap; return { render: frame => frames.push(frame) }; },
    async call(action, fields) {
      calls.push({ action, ...fields });
      if (action in responses) return responses[action](fields, state);
      if (action === 'CATALOG') return { entries: [{ ...lesson, eco: 'C50', variation: 'Giuoco Piano', source: '#' }] };
      if (action === 'START') return lesson;
      if (action === 'THEORY') {
        hint = { NONE: 'PIECE', PIECE: 'SOLUTION', SOLUTION: 'NONE' }[state.hint];
        return state = { ...state, hint, hintPiece: 'PAWN' };
      }
      if (action === 'CLEAR_HINT') return state = { ...state, hint: 'NONE', offLine: false };
      if (action === 'AI_TOGGLE') return state = { ...state, aiEnabled: !state.aiEnabled };
      if (action === 'SEEK') return state = { ...state, ply: fields.index, hint: 'NONE' };
      if (action === 'FORWARD') return state = { ...state, ply: state.ply + 1, hint: 'NONE' };
      if (action === 'ANALYZE') return { evaluation: { whiteCentipawns: 22, whiteShare: .55 }, variants: [] };
      assert.fail('Unexpected worker action ' + action);
    },
  });
  await study.open();
  find('opening-card').click();
  find('opening-dialog-actions').children[0].click();
  await tick();
  return { find, document, tap, frames, calls, study };
}

for (const [locale, title, hint] of [
  ['en', 'Lesson explanation', 'Try moving the pawn.'],
  ['ro', 'Explicația lecției', 'Încearcă să muți piesa pion.'],
]) {
  test(`${locale.toUpperCase()} long caption opens in full and returns focus without duplicate announcements`, async () => {
    const { find, document, tap } = await mountLesson(locale);
    const caption = find('opening-message');
    const live = find('opening-message-announcement');
    assert.equal(caption.tag, 'button');
    assert.equal(caption.attributes.get('aria-label'), title);
    assert.equal(caption.attributes.get('aria-haspopup'), 'dialog');
    assert.equal(live.attributes.get('role'), 'status');
    assert.equal(live.textContent, longPlan);
    assert.equal(live.textWrites, 1);

    caption.focus();
    caption.click();
    const dialog = find('opening-dialog');
    assert.ok(dialog.open);
    assert.equal(dialog.children[0].attributes.has('data-chess-header'), true);
    assert.equal(dialog.children[0].children[1].textContent, title);
    assert.equal(find('opening-message-full').textContent, longPlan);
    assert.equal(dialog.children[0].children[0].attributes.get('aria-label'), locale === 'ro' ? 'Închide' : 'Close');
    dialog.children[0].children[0].click();
    assert.equal(document.activeElement, caption);
    assert.equal(live.textWrites, 1);

    tap('e2');
    assert.equal(live.textWrites, 1);
    find('opening-actions').children[0].click();
    await tick();
    assert.equal(live.textContent, hint);
    assert.equal(live.textWrites, 2);
  });
}

test('The board and leading evaluation meter share one row before and after enabling analysis', async () => {
  const { find, frames } = await mountLesson('en');
  const row = find('opening-board-row');
  const [meter, board] = row.children;
  const controls = find('opening-actions');
  const [guidance, aiToggle, bulb] = controls.children;
  assert.equal(meter.className, 'opening-evaluation-bar');
  assert.equal(board.className, 'board');
  assert.ok(meter.attributes.has('data-withheld'));
  assert.ok(bulb.attributes.has('data-withheld'));
  assert.equal(guidance.textContent, 'Lesson hint');
  aiToggle.click();
  await tick();
  assert.deepEqual(row.children, [meter, board]);
  assert.equal(meter.attributes.has('data-withheld'), false);
  assert.ok(meter.attributes.has('data-measured'));
  assert.equal(bulb.attributes.has('data-withheld'), false);
  assert.equal(aiToggle.attributes.get('aria-pressed'), 'true');
  guidance.click();
  await tick();
  assert.deepEqual(frames.at(-1).targets, []);
  assert.deepEqual(frames.at(-1).marks, ['e2']);
  guidance.click();
  await tick();
  assert.equal(frames.at(-1).selected, 'e2');
  assert.deepEqual(frames.at(-1).targets, ['e3', 'e4']);
  assert.deepEqual(frames.at(-1).marks, ['e2', 'e4']);
  assert.deepEqual(controls.children, [guidance, aiToggle, bulb]);
  assert.equal(guidance.textContent, 'Hide hint');
  const css = readFileSync(new URL('../src/main/web/study-openings.css', import.meta.url), 'utf8');
  assert.match(css, /\.opening-workspace \.opening-actions > button\s*\{[^}]*min-block-size: 48px[^}]*border-radius: 8px/);
  assert.match(css, /\.opening-message-text\s*\{[^}]*block-size: 3lh[^}]*overflow: hidden/);
  assert.match(css, /\.opening-board-row \.square\[data-study-hint\]::before\s*\{[^}]*border-radius: 13%[^}]*#57e0b4[^}]*pointer-events: none/);
  assert.match(css, /inline-size: min\(100%, var\(--play-column\)\)/);
  assert.doesNotMatch(css, /2 \* var\(--play-room\)|max-inline-size: none|repeat\(2, minmax\(0, 1fr\)\);\s*grid-template-rows/);
  assert.match(css, /--opening-board-side:\s*max\(0px, min\(var\(--play-board-limit\)/);
});

function recommendation(centipawns = 22, moves = ['e2e4', 'e2e3']) {
  return { evaluation: { whiteCentipawns: centipawns, whiteShare: .55 },
    variants: moves.map(move => ({ move, previewMoves: moves, depth: 4, proof: { reason: 'HORIZON' },
      frames: [{ fen: '8/8/8/8/8/8/4P3/8', sideToMove: 'WHITE' }] })) };
}

for (const locale of ['en', 'ro']) {
  test(`${locale.toUpperCase()} score and first recommendation arrive together and the bulb only cycles the completed analysis`, async () => {
    let finish;
    const { find, frames, calls } = await mountLesson(locale, {
      ANALYZE: () => new Promise(resolve => { finish = resolve; }),
    });
    const [, toggle, bulb] = find('opening-actions').children;
    const meter = find('opening-evaluation-bar');
    const assessment = find('opening-assessment');
    toggle.click();
    await tick();
    assert.equal(bulb.textContent, '💡');
    assert.equal(bulb.disabled, true);
    assert.equal(meter.attributes.has('data-measured'), false);
    assert.equal(assessment.disabled, true);
    finish(recommendation());
    await tick();
    assert.equal(meter.attributes.has('data-measured'), true);
    assert.equal(assessment.disabled, false);
    assert.match(assessment.textContent, /1\/2 · e2 → e4/);
    assert.deepEqual(frames.at(-1).marks, ['e2', 'e4']);
    assert.equal(bulb.attributes.get('aria-label'), locale === 'ro' ? 'Arată următoarea variantă' : 'Show next best move');
    assessment.click();
    assert.equal(find('opening-dialog').open, true);
    find('opening-dialog').children[0].children[0].click();
    bulb.click();
    await tick();
    assert.match(assessment.textContent, /2\/2 · e2 → e3/);
    assert.deepEqual(frames.at(-1).marks, ['e2', 'e3']);
    assert.match(assessment.textContent, locale === 'ro' ? /\+0,22/ : /\+0\.22/);
    assert.equal(calls.filter(call => call.action === 'ANALYZE').length, 1);
  });
}

test('Hiding and showing AI preserves the completed recommendation and does not search again', async () => {
  const { find, calls, frames } = await mountLesson('en', { ANALYZE: async () => recommendation() });
  const [, toggle, bulb] = find('opening-actions').children;
  toggle.click(); await tick();
  bulb.click(); await tick();
  toggle.click(); await tick();
  assert.equal(find('opening-assessment').attributes.has('data-withheld'), true);
  assert.deepEqual(frames.at(-1).marks, []);
  toggle.click(); await tick();
  assert.match(find('opening-assessment').textContent, /2\/2 · e2 → e3/);
  assert.equal(calls.filter(call => call.action === 'ANALYZE').length, 1);
});

test('A replaced frame and a closed lesson reject late analysis even after returning to the same position', async () => {
  const pending = [];
  const { find, study, calls, frames } = await mountLesson('en', {
    ANALYZE: () => new Promise(resolve => pending.push(resolve)),
  });
  const [, toggle] = find('opening-actions').children;
  const [previous, , next] = find('opening-navigation').children;
  toggle.click(); await tick();
  next.click(); await tick();
  previous.click(); await tick();
  pending[0](recommendation(900)); await tick();
  assert.equal(find('opening-evaluation-bar').attributes.has('data-measured'), false);
  pending[1](recommendation(800)); await tick();
  assert.equal(find('opening-evaluation-bar').attributes.has('data-measured'), false);
  pending[2](recommendation(33)); await tick();
  assert.match(find('opening-assessment').textContent, /\+0\.33/);
  next.click(); await tick();
  assert.equal(calls.filter(call => call.action === 'ANALYZE').length, 4);
  study.close();
  const before = [find('opening-assessment').textContent, frames.length];
  pending[3](recommendation(700)); await tick();
  assert.deepEqual([find('opening-assessment').textContent, frames.length], before);
});

test('One in-flight analysis survives visibility toggles while lesson guidance retains its own cue', async () => {
  let finish;
  const { find, calls, frames } = await mountLesson('en', { ANALYZE: () => new Promise(resolve => { finish = resolve; }) });
  const [guidance, toggle, bulb] = find('opening-actions').children;
  toggle.click(); await tick();
  toggle.click(); await tick();
  guidance.click(); await tick();
  finish(recommendation()); await tick();
  assert.deepEqual(frames.at(-1).marks, ['e2']);
  toggle.click(); await tick();
  assert.deepEqual(frames.at(-1).marks, ['e2']);
  assert.match(find('opening-assessment').textContent, /1\/2 · e2 → e4/);
  bulb.click(); await tick();
  assert.deepEqual(frames.at(-1).marks, ['e2', 'e3']);
  assert.equal(calls.filter(call => call.action === 'ANALYZE').length, 1);
});

test('An empty or failed analysis cannot keep a stale recommendation and a failed request can be retried', async () => {
  let attempt = 0;
  const { find, calls } = await mountLesson('en', { ANALYZE: async () => {
    if (++attempt === 1) throw new Error('worker unavailable');
    return { evaluation: null, variants: [] };
  } });
  const [, toggle, bulb] = find('opening-actions').children;
  toggle.click(); await tick();
  assert.equal(bulb.disabled, true);
  assert.equal(find('opening-assessment').disabled, true);
  toggle.click(); await tick();
  toggle.click(); await tick();
  assert.equal(calls.filter(call => call.action === 'ANALYZE').length, 2);
  assert.equal(bulb.disabled, true);
  assert.equal(find('opening-assessment').disabled, true);
  assert.equal(find('opening-evaluation-bar').attributes.has('data-measured'), false);
});
