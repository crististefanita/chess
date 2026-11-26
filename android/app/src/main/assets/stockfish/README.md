# Pachetul Stockfish Lite Android

## Pe scurt

Pachetul conține configurația Lite Stockfish.js 19.0.1, sursa `0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6`, și rețeaua mică NNUE compatibilă. Executabilele Android sunt construite pentru API 26 și aliniere LOAD de 16 KB pe ARM64, x86_64 și ARMv7. Compilarea nu dovedește acceptarea pe dispozitiv sau forța de joc.

## Intrări și build reproductibil

[Manifestul](manifest.json) identifică arhiva nemodificată, rețeaua, patchul declarat și fiecare executabil. [Controlul pachetului](../../../../../stockfish-package.gradle) verifică pini independenți și metadate ELF înaintea fiecărui APK. Arhiva, licența GPL și autorii însoțesc binarul distribuit.

`LITE_NET=yes` selectează arhitectura compatibilă și `nn-61e7af4bb97d.nnue`. Rețeaua Full nu poate fi substituită. Intrarea nativă a forkului fixat cere exact `source/native-main-unique-ptr.patch`: `uci.loop()` devine `uci->loop()` pentru pointerul unic. Arhiva originală rămâne neschimbată; rețeta consemnează digestul fișierului corectat și ieșirile fiecărui ABI.

```powershell
./android/tools/Build-Stockfish.ps1 -VendorRoot <assets-stockfish> -NdkRoot <NDK-r30> -BuildRoot <fresh-external-directory>
```

Păstrați buildul, logurile compilatorului, observațiile ELF și recepțiile execuției în afara checkout-ului. Promovați pini de executabile doar după compararea artefactelor reale. Android folosește un fir, Hash 32 MiB, 200.000 de noduri cerute și abilitate completă; Lite nu pretinde egalitate cu Full.

## Proveniență și acceptare

- [Sursa upstream fixată](https://github.com/nmrugg/stockfish.js/tree/0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6)
- [Rețeaua compatibilă](https://tests.stockfishchess.org/api/nn/nn-61e7af4bb97d.nnue)
- [Licența GPL](COPYING.txt) și [autorii](AUTHORS)
- [Transport, domeniu și teste](../../../../../../web/stockfish.md)

Android verifică octeții binarului și rețelei, pornește procesul nativ instalat și leagă observațiile de istoricul complet. Eșecul rămâne explicit. Un rezultat host sau Web nu poate substitui observația APK nativă.