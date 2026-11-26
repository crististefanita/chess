# Analiză Stockfish Lite: acceptare și lucrări rămase

## Contractul furnizorului

Stockfish Lite deține indiciile calculate, bara de evaluare și Analiza intermediară/finală pe Web și Android. Basic, Advanced și Hard rămân adversari Native independenți. Referințele de deschidere și soluțiile redactate ale problemelor își păstrează sursa. [Implementare și verificare](../../web/stockfish.md) · [Protocolul de îmbunătățire Native](../testing/hard-ab-elo.md)

| Suprafață | Dovadă necesară |
|---|---|
| Partida AI, ambele culori și orice nivel advers | O observație Lite exactă pentru întregul istoric furnizează bara și cel mult trei recomandări apropiate. Analiza blocată sau eșuată nu poate bloca sau modifica adversarul Native. |
| Tabla de analiză și Deschideri | Schimbările ramurii/cursorului resping răspunsurile vechi; redarea dimensionată după sursă păstrează tabla și linia selectată. Lipsa referinței memorate nu selectează analiza Native. |
| Analiza intermediară și finală | Rădăcinile libere și jucate au aceeași adâncime încheiată și același furnizor. Redeschiderea păstrează identitatea; lucrul parțial sau străin nu este complet. |
| Explicațiile problemelor | Redarea catalogului rămâne cunoaștere verificată prin reguli. Orice linie sau scor calculat cere furnizorul Lite declarat; rutele pentru mutări greșite și câștig trebuie auditate separat. |

Scorul așteptat WDL din bară nu este probabilitatea umană de câștig sau Elo. O PV mai lungă nu dovedește o adâncime încheiată mai mare sau un joc mai puternic. [Convențiile surselor](../board/analysis-sources.md)

## Candidatul implementat

- Modificările live pentru variația deschiderilor și MultiPV complet sunt păstrate. Ambele adaptoare limitează recomandările vizibile la trei; un lot mai adânc întrerupt păstrează ultima cohortă exactă întreagă.
- Web distribuie loaderul/WASM Lite fixate independent și un spațiu de jurnal Lite distinct. Jurnalele Lite noi nu pot moșteni observații Full. Arhivele mai vechi păstrează sursa.
- Lite Android se compilează pentru ARM64, x86_64 și ARMv7 la API 26 cu aliniere LOAD de 16 KB. Patchul nativ declarat al pointerului unic din CLI și rețeta reproductibilă însoțesc arhiva; pinii executabilelor/datelor/sursei sunt verificați la buildul APK.
- Componentele și integrarea Web compilată verifică PV legale, istoric exact, perspectiva scorului, adâncimi complete, anulare, arhive și progres independent al adversarului. Folosiți numerele rulării Extent curente, fără a moșteni PASS-ul altui prototip.

## Lucrări rămase în ordinea priorității

| Prioritate | Lacună | Dovadă de închidere |
|---|---|---|
| P0 | Acceptarea curentă pe platforme și publicarea | APK semnat identificat pe emulator și bundle Lite servit verificat prin Browser MCP, cu aserțiuni originale, UCI, identitate sursă/build și capturi. Buildul sau raportul unui predecesor Full nu sunt suficiente. |
| P1 | Lucru parțial durabil pentru review Android | Persistarea și revalidarea comparațiilor recomandat/jucat pe semimutare după moartea procesului. Reutilizarea RAM Android și analizele finale durabile au domenii separate. Verificați lipsuri, ramuri, scrieri eșuate, schimbări de istoric/furnizor și agregarea finală. |
| P1 | Înregistrarea studiului offline | Păstrați observațiile Lite efective înaintea fiecărei mutări Albe/Negre, decizia Native și review-ul final complet. Indiciile reconstruite ulterior nu pot fi redenumite observații originale. |
| P1 | Calibrarea calității Lite | Comparați contexte independente noi și sacrificii/recapturi cu un profesor mai puternic identificat separat; consemnați diferențe CP/mat/WDL și incertitudinea. Mărimea pachetului nu dovedește forță Full sau Elo Native. |
| P2 | Matricea ciclului de viață și randării | Completați RO/EN cu text mărit, rotație, restaurarea redării deschise și anulare/restart pe APK-ul semnat și artefactul Web servit. Componentele dovedesc propriul domeniu. |

## Dovezi și promovare

Păstrați buildul, UCI brut, recepțiile originale și Extent autonom în `C:\work\sah`. Studiul curent este `studii/2026-11-26-stockfish-lite-release`; predările mai vechi `.studii/stockfish-lite-web` și `.studii/stockfish-android-build` rămân intrări istorice, inclusiv instalările Full eșuate și compilarea nativă anterioară patchului. Nu sunt calificare Lite.

Release-ul cere candidat curat, pachet verificat independent, dovezi de componente/guvernanță, sanity nativ limitat, documentație EN/RO pereche și verificarea downloadului complet pornind din README. Publicarea Pages păstrează octeții runtime-urilor imuabile și verifică bundle-ul servit după deploy. [Procedura de release](../release/README.md#calificare-și-publicare) · [Publicarea Web](../../web/parity.md#publicarea-linkului)

Închideți doar acceptarea demonstrată de rularea identificată. Păstrați aici lucrările restante de ciclu de viață/persistență/calitate; mutați comportamentul durabil în ghidul implementării. Feedbackul forței Native Hard rămâne deschis pe măsurătorile proprii.