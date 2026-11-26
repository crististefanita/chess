# Motorul Stockfish 19 Web

## Pe scurt

Directorul distribuie local cu Chess Lab motorul **Lite pe un singur fir** Stockfish.js 19.0.1. Lite are o rețea neuronală mai mică și nu pretinde aceeași forță ca Full. Loaderul și WASM rămân alăturate, cu octeții originali upstream. Pachetul exclude activele motorului Full.

- [Sursa fixată](https://github.com/nmrugg/stockfish.js/tree/0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6)
- [Arhiva sursei corespunzătoare](https://github.com/nmrugg/stockfish.js/archive/0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6.tar.gz)
- [Release-ul și binarele upstream](https://github.com/nmrugg/stockfish.js/releases/tag/v19.0.1)
- [GNU GPL versiunea 3](COPYING.txt)
- [Autorii Stockfish](AUTHORS)
- [Manifestul distribuției](manifest.json)

Commit sursă: `0d5dd7944f88c817c07a26bc4fb1905d5f9c46d6`. Depozitul upstream conține scripturile și instrucțiunile pentru rețeaua buildului corespunzător. Stockfish și portul păstrează notificările proprii de copyright; această notificare nu relicențiază interfața Chess Lab.

Loaderul JavaScript are 28.230 de octeți, iar WASM 1.845.971. Pinii SHA-256 coincid cu digesturile asset-urilor release-ului upstream. Manifestul consemnează și fișierele reale de licență și autori. Buildul verifică octeții motorului față de constante independente și respinge fișiere Stockfish neașteptate; un manifest modificat nu poate autoriza active substituite sau Full suplimentar. Analizele Full existente își păstrează identitatea și nu sunt redenumite dovezi Lite.