import { showReviewPiece } from './review-workspace.mjs';

const shapes = { PAWN: 'p', KNIGHT: 'n', BISHOP: 'b', ROOK: 'r', QUEEN: 'q' };
const fallback = { p: '♟', n: '♞', b: '♝', r: '♜', q: '♛' };

export function renderMatchCaptures(container, seat, artwork, words) {
    const side = seat?.side;
    const captures = seat?.pieces ?? [];
    const advantage = seat?.advantage ?? 0;
    container.dataset.side = side ?? '';
    container.replaceChildren();
    if (!side || captures.length === 0 && advantage <= 0) {
        container.setAttribute('aria-hidden', 'true');
        container.removeAttribute('aria-label');
        return false;
    }

    const opponentIsWhite = side === 'BLACK';
    const spokenPieces = [];
    for (const captured of captures) {
        const kind = shapes[captured.kind];
        if (!kind || !Number.isInteger(captured.count) || captured.count < 1) continue;
        const piece = container.ownerDocument.createElement('span');
        piece.className = 'captured-piece' + (opponentIsWhite ? ' white' : '');
        piece.dataset.captured = captured.kind;
        piece.setAttribute('aria-hidden', 'true');
        if (!showReviewPiece(piece, kind, artwork)) piece.textContent = fallback[kind];
        container.append(piece);
        if (captured.count > 1) {
            const count = container.ownerDocument.createElement('small');
            count.className = 'captured-piece-count';
            count.textContent = String(captured.count);
            count.setAttribute('aria-hidden', 'true');
            container.append(count);
        }
        const name = words.pieces[captured.kind];
        spokenPieces.push(captured.count > 1 ? name + ' × ' + captured.count : name);
    }

    if (advantage > 0) {
        const badge = container.ownerDocument.createElement('span');
        badge.className = 'match-material-advantage';
        badge.textContent = '+' + advantage;
        badge.setAttribute('aria-hidden', 'true');
        container.append(badge);
    }
    container.setAttribute('aria-hidden', 'false');
    container.setAttribute('aria-label', words.captured(
        words[side.toLowerCase() + '-choice'], spokenPieces.join(', '), advantage));
    return true;
}