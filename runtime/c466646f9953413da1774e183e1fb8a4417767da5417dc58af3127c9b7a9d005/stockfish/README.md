# Stockfish 19 Web engine

## Overview

This directory distributes the **Lite single-thread** Stockfish.js 19.0.1 engine locally with Chess Lab. Lite uses a smaller neural network and is not claimed to match the Full build's playing strength. The loader and WASM remain adjacent and keep their upstream bytes. Full engine assets are excluded from this package.

- [Pinned source](https://github.com/nmrugg/stockfish.js/tree/0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6)
- [Corresponding source archive](https://github.com/nmrugg/stockfish.js/archive/0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6.tar.gz)
- [Upstream release and binaries](https://github.com/nmrugg/stockfish.js/releases/tag/v19.0.1)
- [GNU GPL version 3](COPYING.txt)
- [Stockfish authors](AUTHORS)
- [Distribution manifest](manifest.json)

Source commit: `0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6`. The upstream repository contains the build scripts and network retrieval instructions for the corresponding build. Stockfish and the upstream port retain their respective copyright notices; this notice does not relicense the surrounding Chess Lab interface.

The JavaScript loader is 28,230 bytes and the WASM is 1,845,971 bytes. Their SHA-256 pins match the upstream release asset digests. The manifest also records the actual license and author files. The build checks engine bytes against independent constants and rejects unexpected Stockfish files, so a changed manifest cannot bless substituted or additional Full engine assets. Existing Full analyses keep their original provider identity; they are not relabelled as Lite evidence.
