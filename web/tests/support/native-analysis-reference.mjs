/** Test-only Android/native API compatibility. Never loaded by the Web application.
 * Numeric native fixtures are not evidence about the production Stockfish provider;
 * stockfish-integration.test.mjs and the rendered sanity own that separate contract.
 */
export async function installNativeAnalysisReference(workerUrl) {
    const engine = await import(new URL('./chess-engine.mjs', workerUrl));
    await import(workerUrl);
    const production = self.onmessage;
    self.onmessage = async event => {
        const { id, type, checkpoint, ply, expectedRevision, feature, action, value, index, saved, option, locale, rated = false } = event.data;
        const valid = number => Number.isInteger(number) && number >= 0 && number <= 2147483647;
        try {
            if (type === 'match-analysis-open' && typeof checkpoint === 'string' && valid(ply) && valid(expectedRevision)) {
                self.postMessage({ id, ...JSON.parse(engine.openMatchAnalysis(checkpoint, ply, expectedRevision)) });
            } else if (type === 'study' && ['analysis', 'openings'].includes(feature)
                    && [action, value, saved, option, locale].every(field => typeof field === 'string') && (index === -1 || valid(index))) {
                self.postMessage({ id, study: JSON.parse(engine.studyCommand(feature, action, value, index, saved, option, locale)) });
            } else if (type === 'review-analyze' && typeof checkpoint === 'string' && typeof rated === 'boolean') {
                const canonical = engine.canonicalCheckpoint(checkpoint);
                const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(canonical)));
                const base64 = btoa(String.fromCharCode(...digest));
                const analyze = rated ? engine.analyzeRatedReview : engine.analyzeReview;
                const review = analyze(canonical, base64, () => {});
                self.postMessage({ id, archive: { id: crypto.randomUUID(), checkpoint: canonical, review } });
            } else production(event);
        } catch (error) { self.postMessage({ id, error: String(error) }); }
    };
}
