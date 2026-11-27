/** One localized opponent label for both saved and finished match rows. */
export function matchListOpponent(entry, words) {
    if (entry.mode !== 'AI') return words['open-match'];
    const level = words.levels[entry.level];
    if (!level) throw new TypeError('Unsupported AI level in match list: ' + entry.level);
    if (typeof entry.opponentName !== 'string' || !entry.opponentName) {
        throw new TypeError('Missing saved AI opponent name');
    }
    return `${entry.opponentName} · AI ${level}`;
}
