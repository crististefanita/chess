import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { fileURLToPath } from 'node:url';

/** Offline test adapter. The production client and pinned WASM run unchanged. */
export class NodeStockfishWorker {
    constructor(url) {
        this.commands = [];
        this.transcript = [];
        this.child = spawn(process.execPath, [fileURLToPath(url)], { stdio: ['pipe', 'pipe', 'pipe'] });
        this.closed = new Promise(resolve => this.child.once('close', (code, signal) => {
            this.exit = { code, signal };
            if (!this.expectedClose && code !== 0) this.onerror?.({ message: 'Stockfish process ended: ' + code + '/' + signal });
            resolve(this.exit);
        }));
        this.child.once('error', error => this.onerror?.({ message: error.message }));
        this.child.stdin.on('error', error => this.onerror?.({ message: error.message }));
        this.stderr = '';
        this.child.stderr.on('data', bytes => { this.stderr += bytes.toString(); });
        this.output = createInterface({ input: this.child.stdout });
        this.output.on('line', line => {
            this.transcript.push(line);
            this.onmessage?.({ data: line });
        });
    }
    postMessage(command) {
        this.commands.push(command);
        this.child.stdin.write(command + '\n');
    }
    async finish() {
        this.expectedClose = true;
        if (!this.exit) this.postMessage('quit');
        return this.closed;
    }
    terminate() {
        this.expectedClose = true;
        if (!this.exit) this.child.kill();
        this.output.close();
    }
}
