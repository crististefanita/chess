# Stockfish Lite Android package

## Overview

This package contains the Lite configuration of Stockfish.js 19.0.1, source `0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6`, and its compatible small NNUE. Native Android executables are built for API 26 and 16 KB LOAD alignment on ARM64, x86_64 and ARMv7. Compilation does not establish device acceptance or playing strength.

## Inputs and reproducible build

The [manifest](manifest.json) identifies the unmodified source archive, network, disclosed patch and each executable. The [package guard](../../../../../stockfish-package.gradle) verifies independent pins and ELF metadata before every APK build. The source archive, GPL license and author notices accompany the distributed binary.

`LITE_NET=yes` selects the compatible feature architecture and `nn-61e7af4bb97d.nnue`. The Full network cannot be substituted. The native entry point in the pinned fork needs the exact `source/native-main-unique-ptr.patch`: `uci.loop()` becomes `uci->loop()` for its unique pointer. The original archive remains unchanged; the recipe records the patched file digest and exits for every ABI.

```powershell
./android/tools/Build-Stockfish.ps1 -VendorRoot <assets-stockfish> -NdkRoot <NDK-r30> -BuildRoot <fresh-external-directory>
```

Keep the build, compiler logs, ELF observations and execution receipts outside the checkout. Promote executable pins only after comparing those actual artifacts. Android uses one thread, Hash 32 MiB, 200,000 requested nodes and full skill; Lite is not claimed to equal Full.

## Provenance and acceptance

- [Pinned upstream source](https://github.com/nmrugg/stockfish.js/tree/0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6)
- [Compatible network](https://tests.stockfishchess.org/api/nn/nn-61e7af4bb97d.nnue)
- [GPL license](COPYING.txt) and [authors](AUTHORS)
- [Transport, scope and tests](../../../../../../web/stockfish.md)

Android verifies binary and network bytes, starts the installed native process and binds observations to full game history. Failure remains explicit. A host or Web result cannot stand in for a native APK observation.