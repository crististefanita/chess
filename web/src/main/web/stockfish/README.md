# Stockfish 19 Web engine

This directory distributes the **full single-thread** Stockfish.js 19.0.1 engine locally with Chess Lab. It is not the Lite build. The loader and WASM remain adjacent and keep their upstream bytes.

- [Pinned source](https://github.com/nmrugg/stockfish.js/tree/0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6)
- [Corresponding source archive](https://github.com/nmrugg/stockfish.js/archive/0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6.tar.gz)
- [Upstream release and binaries](https://github.com/nmrugg/stockfish.js/releases/tag/v19.0.1)
- [GNU GPL version 3](COPYING.txt)
- [Stockfish authors](AUTHORS)
- [Distribution manifest](manifest.json)

Source commit: `0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6`. The upstream repository contains the build scripts and network retrieval instructions for the corresponding build. Stockfish and the upstream port retain their respective copyright notices; this notice does not relicense the surrounding Chess Lab interface.

The JavaScript loader is 28,130 bytes and the WASM is 99,169,054 bytes. The manifest records their pinned upstream SHA-256 values and the actual license and author files. The build also checks the binaries against independent pinned constants, so editing the manifest cannot bless substituted engine bytes.
