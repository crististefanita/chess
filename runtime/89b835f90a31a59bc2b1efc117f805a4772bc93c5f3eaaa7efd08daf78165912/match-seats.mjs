/** Places the same named players beside their captured pieces as the native board. */
export function matchSeatLabels(match, humanName, words) {
    const bottomSide = match.blackBelow ? 'BLACK' : 'WHITE';
    const active = match.viewPly < match.ply || match.result === 'IN_PROGRESS' ? match.sideToMove : null;
    const label = side => {
        const name = match.mode === 'AI'
            ? side === match.humanSide ? humanName : `${match.opponentName} · AI ${words.levels[match.level]}`
            : words[side.toLowerCase() + '-choice'];
        return { side, name, text: (side === active ? '●  ' : '') + name,
            description: `${name} · ${words[side.toLowerCase() + '-choice']}${side === active ? ' · ' + words.toMove : ''}`,
            active: side === active };
    };
    return { top: label(bottomSide === 'WHITE' ? 'BLACK' : 'WHITE'), bottom: label(bottomSide) };
}

export function renderMatchSeat(element, seat) {
    element.textContent = seat.text;
    element.dataset.side = seat.side;
    element.dataset.active = String(seat.active);
    element.setAttribute('aria-label', seat.description);
    element.setAttribute('title', seat.name);
}
