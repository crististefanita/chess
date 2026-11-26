# Profilurile Native curente: calibrare și rejucare

[Strategia de testare](README.md) · [Priorități Hard deschise](../feedback/hard.md) · [Protocol A/B](hard-ab-elo.md)

## Scop și limite

Măsoară Basic, Advanced și Hard independent de furnizorul de coaching. O măsurare validă poate arăta un jucător slab; rejucarea corectă a dovezilor nu înseamnă că toate puzzle-urile, PV-urile ori cererile de coaching au trecut. Este măsurare JVM offline, fără calificare browser, Android, APK sau release.

Înghețarea curentă este `3389013a9062a41fcc0ae85717bbdbd67012760e`. Oponentul public este `LEGACY`. Sursa de producție, politicile și clasele compilate sunt fixate înaintea jocului. H3 comun este exclus din contorul `LEGACY_EXTRA`. Politica adaptivă admite până la 200k noduri suplimentare; căutările din repertoriu ori încheiate devreme pot consuma mai puțin. Nu descrie fiecare decizie drept 44k sau 200k.

## Plan înghețat

- Cincizeci de poziții distincte din zece familii de deschideri scrise; cinci poziții per familie, la opt până la șaisprezece semimutări. Sunt familii de repertoriu consumate, **nu transfer independent nou**.
- Fiecare profil joacă ambele culori din fiecare poziție: cincizeci de perechi și o sută de partide per profil, trei sute în total. Pozițiile inițiale și alegerile de deschidere sunt identice între profiluri.
- Adversar: binarul desktop Stockfish 19 fixat, `UCI_LimitStrength=true`, `UCI_Elo=1500`, 6.000 de noduri cerute, un fir și hash 16 MiB. Ancora configurată nu stabilește rating uman/FIDE.
- O partidă activă la 220 de semimutări totale rămâne neterminată. Păstrează checkpoint-ul și raporteaz-o separat de remize.
- Șase partide sunt selectate **înaintea jocului** pentru coaching Stockfish Lite real: o pereche de culori per profil. Fiecare mutare alb/negru după prefix încearcă un indiciu Lite de 200k, MultiPV 3; urmează review-ul complet al produsului. Celelalte partide de rating nu intră la învățare doar fiindcă există PGN-ul lor.

Ancora este Stockfish 19 desktop. Profesorul este WASM Lite fixat, prin CLI Node și transportul produsului. Binarele, opțiunile și responsabilitățile sunt diferite. Proba nu califică adaptoarele Web sau Android. Indiciul lipsă nu schimbă alegerea Native.

## Rezultate măsurate

| Profil | Performanță pe partide încheiate | Anvelopă descriptivă 95% prin bootstrap de familii | V / R / Î / neterminate | Neterminate tratate toate ca înfrângeri…victorii |
|---|---:|---:|---|---:|
| Basic | 994 | 702…1.185 | 4 / 2 / 91 / 3 | 988…1.076 |
| Advanced | 1.366 | 1.290…1.469 | 26 / 5 / 59 / 10 | 1.340…1.419 |
| Hard | 1.663 | 1.531…1.763 | 61 / 3 / 23 / 13 | 1.589…1.696 |

Punctul folosește doar partidele încheiate: `p=(wins+draws/2)/(wins+draws+losses)`, apoi `1500+400*log10(p/(1-p))`. Poate fi deplasat când neterminatele diferă sistematic; limitele separate de sensibilitate expun acest risc. Nu acorda tacit jumătate de punct neterminatelor. Intervalul descriptiv reeșantionează familii întregi de deschidere, păstrând toate cele cinci perechi de culori, cu 20.000 de extrageri și seed 41273; învelește extremele de cenzurare toate-înfrângeri/toate-victorii. Zece familii și o ancoră configurată lasă incertitudine semnificativă. Limitele infinite rămân infinite.

Este un **reper nou**, nu Delta Elo măsurată pentru candidat ori dovadă de progres față de alt protocol. Nu combina familiile repetate de repertoriu cu eșantionul istoric de 600 de partide, nu schimba automat Elo-ul afișat și nu prelua ratingul Stockfish. Candidatul are nevoie de propria comparație pereche înghețată și de porțile de promovare.

| Profil | Prima mutare acceptată / 1.238 | Traseu scris complet / 1.238 | Decizii Native efective |
|---|---:|---:|---:|
| Basic | 853 | 773 | 1.622 |
| Advanced | 1.114 | 1.067 | 1.977 |
| Hard | 1.214 | 1.188 | 2.198 |

Hard încheie toate cele 1.000 de exerciții de mat pe traseele scrise. Exercițiile materiale sunt complete **188/238**: tactică 87/100, antrenament 79/114 și mutare intermediară 22/24. Cincizeci rămân incomplete. Benchmarkul cheamă oponentul obișnuit înainte să citească verdictul quest-ului; nu oferă căutării obiectivul de mat, soluția sau apărarea următoare. Traseul integral valid aici acoperă apărarea scrisă în catalog, **nu orice apărare alternativă liberă**. Dovezile separate pe toate apărările își păstrează domeniul original în [Starea Hard](hard-status.md).

## Eșecurile colectorului și continuarea tehnică

Colectorul original a oprit cinci partide: trei la verificări Native prea restrictive și două în coaching Lite. Prefixele și eșecurile reale rămân imuabile. Cinci continuări reutilizează exact mutările și observațiile originale, apoi pornesc altă sesiune de ancoră cu aceleași setări și tot istoricul. Jucătorul Stockfish limitat este nedeterminist; resetările tehnice ale sesiunii sunt declarate, nu numite continuări bit-identice ale hash-ului sau stării sale aleatoare. Reîncercările de la început sunt păstrate separat și excluse din lot.

Două decizii Native diferă legitim de scorul brut maxim prin politica existentă de istoric/repetiție. `AiProfileEvidence` rejoacă independent acel contract de selecție. Respinge în continuare o mutare legală arbitrar substituită, cost schimbat, clasament gol, execuție străină, fișier modificat ori remiză inventată. Se repară o **presupunere a colectorului**, nu căutarea.

A treia recepție Native conține o continuare legală de șapte semimutări cu capturi sub eticheta H5 obișnuită, fără certificat tipizat separat de închidere. Rezultatul partidei se poate rejuca legal fără ca acea continuare să devină dovadă adversarială completă. Obiectivul de calitate rămâne **FAIL**. Verificatorul strict de puzzle/dovadă se păstrează, fără slăbire pentru a face cazul verde.

Patru dintre cele șase partide Lite predeclarate au toate indiciile dinaintea mutărilor și review complet. Cele două partide Advanced păstrează trei încercări de indiciu indisponibil și două review-uri indisponibile, cu `No exact PV for Stockfish bestmove`. **Nu intră la învățare**. Apelurile de transport eșuate nu expun întregul transcript raw în obiectul erorii; capturarea lui înaintea unei corecții de furnizor rămâne în [feedback-ul Lite](../feedback/stockfish-web.md). Coaching-ul complet permite studiul ulterior, nu certifică independent H17/H19 fiecare continuare oferită.

## Audit și repetare

1. Îngheață sursa, configurația, classpath-ul, starturile, partițiile, identitățile furnizorilor și selecția **înainte** de măsurare. Identifică separat colectorul schimbat; nu recompila clasele jucătorilor în timpul rundei.
2. Păstrează observația Native originală și UCI-ul ancorei înaintea fiecărei mutări. Reține PGN, checkpoint, ID de execuție, rezultat și registrul raw legat. Pentru partidele de învățare selectate păstrează încercările Lite pe ambele tururi și review complet sau eroarea tipizată.
3. Rejoacă fiecare PGN prin chess.js 1.4.0 fixat independent; compară istoricul UCI legal și rezultatul terminal. Rejoacă contabilizarea Native, selecția dependentă de istoric și verdictele puzzle-urilor prin contractele JVM înghețate. Leagă cohortele Lite de UCI raw, context complet, furnizor și adâncime comună.
4. Respinge perechile incomplete, partidele duplicate și eșantioanele inegale între familii înainte de estimare. Păstrează explicit partidele plafonate și continuările tehnice. `AiProfileRatingTest` verifică rezultate cunoscute, zero partide încheiate, infinituri, duplicate, culori lipsă și dezechilibrul familiilor.
5. Generează rezultate ATF Extent originale separate pentru **integritatea măsurării**, contractele componentelor și **obiectivele de calitate**. PASS la importul dovezilor nu poate suprascrie o dovadă Native eșuată, review incomplet sau catalog nerezolvat.
6. Actualizează ghidul proprietar și feedback-ul doar după verificarea rezultatelor originale și a rapoartelor. Calibrarea diagnostică nu declanșează APK și nu schimbă ratingul calificat din aplicație.

Rădăcină constantă: `C:\work\sah`; colecția relativă curentă `.studii/native-measurement/`. README-ul indexează planul înghețat, eșecurile originale, continuările prefixului exact, rejucarea PGN independentă, registrele Native/puzzle, ratingurile, Extent original de componente și cele trei obiective de calitate explicit eșuate. Buildurile și rezultatele stau în afara repository-ului. Micul fixture fixat de contraexemple servește exclusiv testelor, nu este repertoriu runtime FEN–mutare.
