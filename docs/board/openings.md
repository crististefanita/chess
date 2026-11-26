# Deschideri

## Pe scurt

Vezi [sursele analizei și liniile selectabile](analysis-sources.md) pentru scorul de referință Stockfish, estimarea nativă a redării și contractul selectorului cu trei linii.

- **Domeniu.** Exersează o linie adnotată din catalog cu oricare culoare. Tabla de analiză poate afișa și continuările documentate pentru poziția exactă.
- **Probe.** `OpeningCatalogTest` verifică datele și mutările legale, `OpeningLessonTest` verifică jocul și reluarea, iar `OpeningTheoryTest` verifică găsirea teoriei. `ChessWebOpeningStudyTest` verifică comenzile Web.

- Din meniul principal, intră în **Deschideri**, alege o linie și joacă cu albele sau cu negrele. Tabla se orientează cu piesele tale jos. Aplicația redă replicile din lecție și explică ideea fiecărei mutări. O mutare legală din altă variantă lasă lecția la pasul curent.

| Lecție | Poziția studiată | Ideea urmărită |
|---|---|---|
| Partida italiană | Giuoco Pianissimo, C54 | Centrul susținut de c3 și d3, dezvoltarea și rocada. [Chess.com](https://www.chess.com/openings/Italian-Game) |
| Partida spaniolă | Varianta închisă cu 6.d3, C84 | Presiunea pe calul de pe c6 și consolidarea pionului e4. [Chess.com](https://www.chess.com/openings/ruy-lopez-opening) · [Linia C84 în catalogul Lichess](https://github.com/lichess-org/chess-openings/blob/master/c.tsv#L1193) |
| Apărarea Caro–Kann | Avans, varianta Short, B12 | Nebunul iese înainte de e6, apoi negrul atacă baza lanțului de pioni. [Chess.com](https://www.chess.com/openings/Caro-Kann-Defense-Advance-Variation) · [Linia Short în catalogul Lichess](https://github.com/lichess-org/chess-openings/blob/master/b.tsv#L302) |
| Gambitul damei refuzat | Apărarea ortodoxă, D63 | Negrul susține d5 și dezvoltă piesele de pe flancul regelui. [Chess.com](https://www.chess.com/openings/Queens-Gambit-Declined-Orthodox-Defense) |
| Apărarea siciliană | Najdorf, B90 | Schimbul pionului c pentru pionul d și controlul câmpului b5 cu a6. [Chess.com](https://www.chess.com/openings/Sicilian-Defense-Open-Najdorf-Variation) |
| Partida scoțiană | Atacul Blackburne, C45 | Deschiderea rapidă a centrului și presiunea asupra calului de pe d4. [Chess.com](https://www.chess.com/openings/Scotch-Game) · [Linia Blackburne în Lichess](https://github.com/lichess-org/chess-openings/blob/master/c.tsv#L753) |
| Apărarea franceză | Avans, C02 | Albului îi revine spațiul de pe e5, iar negrul atacă baza d4 cu c5. [Chess.com](https://www.chess.com/openings/French-Defense-Advance-Variation) · [Linia cu 6.a3 în Lichess](https://github.com/lichess-org/chess-openings/blob/master/c.tsv#L60) |
| Apărarea scandinavă | Varianta clasică, B01 | Dezvoltare cu tempo asupra damei și eliberarea nebunului cu Bf5. [Linia clasică în Lichess](https://github.com/lichess-org/chess-openings/blob/master/b.tsv#L95) |
| Sistemul Londonez | Împotriva fianchetto-ului, A48 | Bf4 înainte de e3 și dezvoltarea pentru rocada ambelor părți. [Chess.com](https://www.chess.com/openings/London-System) · [Linia cu Be2 în Lichess](https://github.com/lichess-org/chess-openings/blob/master/a.tsv#L642) |
| Deschiderea engleză | Patru cai și fianchetto, A29 | Controlezi d5 cu pionul c și răspunzi la ruptura centrală înainte de rocada albului. [Linia cu Nb6 în Lichess](https://github.com/lichess-org/chess-openings/blob/3ba6b20d31b2e6125cb0e6a2e792f400717d9d39/a.tsv#L453) |
| Deschiderea catalană | Catalana deschisă, linia clasică, E05 | Dezvolți nebunul pe g2 și faci rocada înainte de a decide cum recuperezi pionul de pe c4. [Linia clasică în Lichess](https://github.com/lichess-org/chess-openings/blob/3ba6b20d31b2e6125cb0e6a2e792f400717d9d39/e.tsv#L17) |
| Apărarea Nimzo-indiană | Clasică, apărarea Keres, E32 | Negrul leagă calul de pe c3, iar albul pregătește recaptura cu dama pentru a păstra structura de pioni. [Linia Keres în Lichess](https://github.com/lichess-org/chess-openings/blob/3ba6b20d31b2e6125cb0e6a2e792f400717d9d39/e.tsv#L169) |
| Siciliana Alapin | Apărarea Barmen, linia modernă, B22 | Albul pregătește d4 prin c3, iar negrul răspunde din timp cu d5. [Linia Barmen în Lichess](https://github.com/lichess-org/chess-openings/blob/3ba6b20d31b2e6125cb0e6a2e792f400717d9d39/b.tsv#L451) |
| Apărarea est-indiană | Aronin–Taimanov, E97 | Centrul larg al albului, contraatacul cu e5 și poziția închisă după d5. [Linia Aronin–Taimanov în Lichess](https://github.com/lichess-org/chess-openings/blob/master/e.tsv#L358) |

- Săgețile deasupra tablei parcurg câte o semimutare. În istoric vezi poziția și explicația mutării alese. Atinge progresul pentru a sări la o poziție jucată. **Rejoacă de aici** reia lecția de la poziția afișată. Comanda sistemului **Înapoi** revine din istoric la poziția curentă.

- La poziția curentă, săgeata înainte aplică exact următoarea mutare din lecție. Când replica din catalog rămâne în așteptare, mai apasă o dată săgeata sau folosește **Continuă lecția**. Dacă joci cu piesele pe tablă, replica vine automat. La final poți reveni la orice pas sau exersa din nou.

- Pe aceeași fereastră, tabla păstrează poziția și dimensiunea când parcurgi mutările. Progresul și butoanele își rezervă înălțimea, iar **Indiciu lecție**, **Continuă lecția** și **Rejoacă de aici** folosesc același loc.

- **Indiciu lecție** evidențiază întâi piesa. **Arată mutarea** selectează piesa, trasează săgeata și afișează coordonatele și ideea. Atinge destinația marcată ca să joci mutarea.

- **Analiză AI** evaluează poziția afișată, inclusiv în istoric și la finalul lecției. Scorul, bara și prima recomandare apar împreună dintr-o singură analiză încheiată. Becul monocrom folosește aceeași nuanță verde activă ca jocul AI și parcurge continuările disponibile fără o nouă căutare. Ascunderea și reafișarea AI în aceeași poziție reutilizează rezultatul; schimbarea poziției îl șterge, iar un răspuns întârziat nu poate înlocui cadrul nou. Indiciul lecției își păstrează propria mutare evidențiată. Când sugestia AI coincide cu mutarea ta din lecție, o poți juca atingând destinația. O sugestie diferită apare etichetată drept variantă AI. [Politica indiciilor AI](ai-quality.md)

## Configurarea lecțiilor

- **Ordine și identitate.** Ordinea din `openings.yaml` stabilește ordinea din meniu. Fiecare lecție are ID stabil, cod ECO, sursă HTTPS, titlu, variantă și plan în română și engleză.
- **Mutări și idei.** În `line`, fiecare pas conține mutarea UCI și ideea în ambele limbi. Pentru rocadă se declară mutarea regelui, de exemplu `e1g1`.
- **Validare.** Motorul reia toate mutările de la poziția inițială și indică lecția și pasul dacă găsește o mutare ilegală. [Catalogul YAML](../../chess-core/src/main/resources/openings.yaml)
- **Adăugarea unei linii.** Verificați codul ECO și mutările din sursă. Explicați ce schimbă mutarea pe tablă și ce plan pregătește. Păstrați ID-ul la editarea textului și adăugați o intrare distinctă pentru o variantă nouă.
- **Verificarea schimbării.** Rulați suita de componente pentru ambele culori și proba Android pentru navigare, rejucare și indicii. [Setul ECO Lichess](https://github.com/lichess-org/chess-openings) · [Strategia de testare](../testing/README.md#deschideri)

## Indicii de teorie pe tabla de analiză

- **Poziția.** Pe [Tabla de analiză](README.md#tablă-de-analiză), indiciile caută traseele din poziția exactă, inclusiv rocada și en passant. După fiecare mutare aplicația caută noua poziție în întregul catalog, inclusiv după o mutare care nu apărea în sugestiile anterioare.
- **Traseele din catalog.** Lecțiile YAML oferă până la trei trasee în ordinea din catalog. Numărul afișat urmează disponibilitatea din poziția respectivă. Traseele cu aceeași primă mutare rămân distincte când continuă diferit și păstrează toate semimutările și explicațiile rămase.
- **Redare.** Eticheta de sub tablă deschide redarea pe tot ecranul. Schimbarea traseului sau a pasului păstrează tabla și săgețile fixe. Fiecare traseu își păstrează pasul cât timp fereastra este deschisă, iar Înapoi revine la aceeași poziție. [Redarea în Game Review](game-review.md#navigarea-în-review-și-variantele-recomandate)
- **Rezervă.** Fără lecție YAML aplicabilă, aplicația folosește continuări complete din indexul ECO. Traseele ECO nu au adnotările lecțiilor. Fără continuare documentată, becul revine la analiza motorului.

## Referințe măsurate pentru deschideri

[OpeningReference](../../chess-core/src/main/java/ro/atf/chess/OpeningReference.java) citește [opening-reference.yaml](../../chess-core/src/main/resources/opening-reference.yaml) pentru prefixul legal exact al lecției. Cele 139 de prefixe unice acoperă fiecare cadru al catalogului actual; 133 păstrează cel puțin două rădăcini apropiate, iar 122 le păstrează pe toate trei. Cheia păstrează istoricul UCI complet, culoarea la mutare și drepturile de rocadă/en passant refăcute prin redare. Cititorul istoric al referinței pornește Native pentru un prefix absent. Analiza calculată a adaptoarelor Web și Android curente folosește furnizorul extern Lite pentru istoricul exact. Producătorul invalid, liniile ilegale sau duplicate și deltele inconsistente sunt respinse la încărcare.

- **Sursă măsurată.** Stockfish 19, adâncime 16, un fir, hash de 16 MiB și MultiPV 3, cu partidă nouă și hash gol pentru fiecare prefix. YAML-ul consemnează amprenta binarului. Sunt evaluări de referință cu orizont limitat, fără pretenții de scor universal sau creștere a forței native.
- **Un singur rezultat.** Bara și cel mult trei continuări legale folosesc aceeași intrare și adâncime finalizată. Se păstrează doar mutările la cel mult 40 cp de lider. Mutarea scrisă în lecție nu devine automat cea mai bună mutare măsurată. Referința nu are recepție nativă de dovadă forțată.
- **Variația adversarului.** Partidele AI noi folosesc aceleași rădăcini Stockfish memorate pentru prefixul exact, rotite pseudoaleator cu ordinalul salvat. Suprascrierile verificate au prioritate; istoricele neacoperite păstrează teoria și căutarea. Fără proces Stockfish live pentru mutarea adversarului, iar salvările vechi păstrează alegerea deterministă. Java și YAML comune aliniază Web/APK.

- **Delta.** Scorurile sunt din perspectiva albului: cp curent minus cp al prefixului precedent; delta inițială este zero. Rotirea tablei sau alegerea negrului nu inversează convenția. Ambele adaptoare afișează **Ref. Δ** și expun producătorul/adâncimea.
- **Regenerare.** Exportă prefixele catalogului cu [ExportOpeningReferenceInputs](../../scripts/ExportOpeningReferenceInputs.java), apoi rulează [generate-opening-reference.cjs](../../scripts/generate-opening-reference.cjs) cu fișierul de prefixe, binarul Stockfish 19, YAML-ul rezultat și jurnalul UCI brut. Păstrează jurnalele în afara checkout-ului. [Dovezi de componentă](../testing/README.md#componente-pentru-reutilizarea-analizei)

## Dimensiunea ferestrei de redare

Dialogul recomandării preia lățimea tablei sursă înainte de deschidere, prin adaptorul comun rememberBoardSize. Chenarul adaugă doar spațierea și bordurile acelui pătrat, limitat de fereastra curentă și de reperul comun al Tablei de analiză. Lățimea fixă a dialogurilor informative nu mai micșorează această redare. Taburile și Înapoi/Înainte reutilizează dimensiunea capturată; redeschiderea citește din nou sursa curentă.

Când sunt disponibile două sau trei opțiuni de indiciu, butoanele împart lățimea redării pe un singur rând. Textul rămâne pe un singur rând și se poate trunchia doar dacă fereastra este mai îngustă decât eticheta; controlul păstrează înălțimea minimă de 48 px. Regula este limitată la redare și nu schimbă împachetarea celorlalte grupuri de linii.

Regula se aplică numai redării recomandării. Selecția lecției, istoricul și explicațiile își păstrează limitele existente. Ferestrele înguste ori scunde pot limita pătratul sau derula dialogul când conținutul lizibil complet nu încape. [Componente](../testing/README.md#componente-pentru-reutilizarea-analizei) · [Adaptorul de dimensiune](../../web/src/main/web/study-board-size.mjs)
