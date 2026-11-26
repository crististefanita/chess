import { assert, test } from './support/atf-node.mjs';
import { renderAnalysisMeter } from '../src/main/web/study-analysis.mjs';

function meter() {
    const attributes = new Map();
    return { attributes, setAttribute: (key, value) => attributes.set(key, String(value)),
        removeAttribute: key => attributes.delete(key) };
}

test('Moderate analysis keeps its share through both board orientations', () => {
    for (const blackBelow of [false, true]) {
        const track = meter(), fill = { style: {} };
        renderAnalysisMeter(track, fill, { ending: 'HORIZON', whiteShare: .6, centipawns: 100 }, blackBelow, 'en');
        assert.equal(fill.style.height, '60%');
        assert.equal(fill.style.insetBlockStart, blackBelow ? '0' : 'auto');
        assert.equal(fill.style.insetBlockEnd, blackBelow ? 'auto' : '0');
        assert.match(track.attributes.get('aria-valuetext'), /\+1.00/);
    }
});

test('A finite large advantage cannot round the rendered track to a terminal endpoint', () => {
    for (const whiteShare of [.996031746031746, .003968253968253954]) {
        const track = meter(), fill = { style: {} };
        renderAnalysisMeter(track, fill, { ending: 'HORIZON', whiteShare, centipawns: 50000 }, false, 'en');
        assert.equal(Number.parseFloat(fill.style.height), whiteShare * 100);
        assert.ok(Number.parseFloat(fill.style.height) > 0 && Number.parseFloat(fill.style.height) < 100);
    }
});

test('Mate endpoints and unavailable analysis retain their separate display states', () => {
    const track = meter(), fill = { style: {} };
    for (const [share, mate, height] of [[1, 2, '100%'], [0, -2, '0%']]) {
        renderAnalysisMeter(track, fill, { ending: 'CHECKMATE', whiteShare: share, mate }, false, 'en');
        assert.equal(fill.style.height, height);
        assert.match(track.attributes.get('aria-valuetext'), /Mate/);
    }
    renderAnalysisMeter(track, fill, null, false, 'en');
    assert.equal(track.attributes.get('aria-hidden'), 'true');
    assert.equal(track.attributes.has('aria-valuenow'), false);
    assert.equal(track.attributes.has('aria-valuetext'), false);
    assert.equal(fill.style.height, '0%');
});
