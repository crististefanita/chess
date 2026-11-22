const positiveGrades = new Set(['BRILLIANT', 'GREAT', 'BEST']);
const warningGrades = new Set(['MISTAKE', 'MISS', 'BLUNDER']);
const terminalResults = new Set([
    'WHITE_WINS', 'BLACK_WINS', 'STALEMATE', 'MATERIAL_DRAW', 'REPETITION_DRAW', 'MOVE_COUNT_DRAW', 'AGREED_DRAW',
]);

export function reviewGradeTone(grade) {
    if (positiveGrades.has(grade)) return 'positive';
    if (warningGrades.has(grade)) return 'warning';
    return 'neutral';
}

export function matchReviewActionState(match, {
    browsing = false, running = false, engineFailed = false, otherTabChanged = false,
} = {}) {
    const available = match?.menu?.GAME_REVIEW === true;
    return {
        visible: Boolean(match && available && !browsing && terminalResults.has(match.result)),
        disabled: !available || running || engineFailed || otherTabChanged,
    };
}
