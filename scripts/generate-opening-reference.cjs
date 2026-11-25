const fs = require('node:fs/promises');
const crypto = require('node:crypto');
const { spawn } = require('node:child_process');
const readline = require('node:readline');

// Inputs come from ExportOpeningReferenceInputs, not a second YAML parser.
async function main() {
  const [inputs, binary, output, evidence] = process.argv.slice(2);
  if (!evidence) throw new Error('Provide prefixes TSV, Stockfish 19 executable, output YAML, and raw UCI log');
  const prefixes = (await fs.readFile(inputs, 'utf8')).replace(/\r/g, '').trimEnd().split('\n');
  if (prefixes[0] !== '' || new Set(prefixes).size !== prefixes.length) throw new Error('Expected unique prefixes including the initial position');
  const engine = spawn(binary, [], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  const queue = [];
  let consume;
  let failure;
  const raw = [];
  const deliver = line => { raw.push(line); if (consume) { const resolve = consume; consume = null; resolve(line); } else queue.push(line); };
  readline.createInterface({ input: engine.stdout }).on('line', deliver);
  engine.on('error', error => { failure = error; if (consume) consume(null); });
  engine.on('exit', code => { failure = new Error('Stockfish exited: ' + code); if (consume) consume(null); });
  engine.stderr.on('data', bytes => raw.push('stderr: ' + bytes));
  const send = line => { raw.push('> ' + line); engine.stdin.write(line + '\n'); };
  async function next() {
    if (queue.length) return queue.shift();
    if (failure) throw failure;
    let timer;
    try {
      return await Promise.race([new Promise(resolve => { consume = resolve; }),
        new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('UCI timeout')), 60000); })]);
    } finally { clearTimeout(timer); }
  }
  async function until(end) { const lines = []; for (;;) { const line = await next(); if (line === null) throw failure; if (!line) continue; lines.push(line); if (end(line)) return lines; } }
  try {
    send('uci');
    const hello = await until(line => line === 'uciok');
    const name = hello.find(line => line.startsWith('id name '))?.slice(8);
    if (name !== 'Stockfish 19') throw new Error('Expected Stockfish 19, got ' + name);
    for (const option of ['Threads value 1', 'Hash value 16', 'UCI_LimitStrength value false', 'MultiPV value 3']) send('setoption name ' + option);
    send('isready'); await until(line => line === 'readyok');
    const records = [];
    const byPrefix = new Map();
    for (const moves of prefixes) {
      send('ucinewgame'); send('setoption name Clear Hash'); send('isready'); await until(line => line === 'readyok');
      send('position startpos' + (moves ? ' moves ' + moves : ''));
      send('go depth 16');
      const lines = await until(line => line.startsWith('bestmove '));
      const variations = new Map();
      for (const line of lines) {
        if (!line.includes('depth 16 ') || /lowerbound|upperbound/.test(line)) continue;
        const match = line.match(/\bmultipv (\d+).*?\bscore cp (-?\d+).*?\bpv (.+)$/);
        if (match) variations.set(+match[1], { score: +match[2], line: match[3].trim() });
      }
      if (variations.size !== 3) throw new Error('Incomplete exact MultiPV at ' + moves);
      const sorted = [...variations].sort((a,b) => a[0]-b[0]).map(([,v]) => v);
      if (lines.at(-1).split(' ')[1] !== sorted[0].line.split(' ')[0]) throw new Error('Bestmove mismatch');
      const side = moves ? moves.split(' ').length % 2 : 0;
      const score = sorted[0].score * (side ? -1 : 1);
      const previous = moves.includes(' ') ? moves.slice(0, moves.lastIndexOf(' ')) : '';
      const record = { moves, 'white-cp': score, 'delta-white-cp': moves ? score - byPrefix.get(previous)['white-cp'] : 0,
        lines: sorted.filter(v => sorted[0].score-v.score <= 40).map(v => ({ 'white-cp': v.score * (side ? -1 : 1), moves: v.line })) };
      records.push(record); byPrefix.set(moves, record);
      console.log(records.length + '/' + prefixes.length + ' ' + moves);
    }
    const reference = { schema: 1, source: { engine: name, depth: 16, threads: 1, 'hash-mb': 16, 'multi-pv': 3,
      'max-gap-cp': 40, perspective: 'WHITE', 'engine-sha256': crypto.createHash('sha256').update(await fs.readFile(binary)).digest('hex') }, positions: records };
    const yaml = ['schema: 1', 'source:', ...Object.entries(reference.source).map(([key,value]) =>
      '  ' + key + ': ' + JSON.stringify(value)), 'positions:'];
    for (const row of records) {
      yaml.push('  - moves: ' + JSON.stringify(row.moves), '    white-cp: ' + row['white-cp'],
        '    delta-white-cp: ' + row['delta-white-cp'], '    lines:');
      for (const line of row.lines) yaml.push('      - white-cp: ' + line['white-cp'], '        moves: ' + JSON.stringify(line.moves));
    }
    await fs.writeFile(output, yaml.join('\n') + '\n');
  } finally {
    await fs.writeFile(evidence, raw.join('\n') + '\n');
    engine.stdin.end('quit\n');
    setTimeout(() => engine.kill(), 1000).unref();
  }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
