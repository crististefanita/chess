/** Names the observed build without relabelling archived Full observations as Lite. */
export function stockfishLabel(provider) {
    const id = provider?.providerID;
    if (typeof id !== 'string' || !id.startsWith('stockfish-')) return '';
    if (/(?:^|-)lite(?:-|$)/.test(id)) return 'Stockfish 19 Lite';
    if (/(?:^|-)full(?:-|$)/.test(id)) return 'Stockfish 19 Full';
    return 'Stockfish 19';
}
