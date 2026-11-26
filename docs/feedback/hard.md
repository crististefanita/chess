# Feedback Hard · nucleu experimental

## Priorități

Sarcina curentă definește cerințe și îmbunătățire offline controlată. Livrarea APK/RAG are proprietar separat. Stockfish Lite deține analiza produsului cerută; adversarul Java trebuie să progreseze prin propriile măsurători. [Protocol A/B și Elo](../testing/hard-ab-elo.md) · [Cerințele analizei](stockfish-web.md)

| Prioritate | Caz | Dovadă necesară |
|---|---|---|
| P0 | Înghețarea reperului real | Citiți sursa/YAML live și selectorul efectiv. Păstrați schimbările paralele de deschideri. Digesturi sursă/build/politică și o decizie/raport efectiv per mutare. Fără Elo experimental sau istoric moștenit. |
| P0 | Contractul auditului istoric | `CoachingProofTest#retainsActualReserveWorkAndHistoricalReceiptShapes` a expus patru câmpuri BotDecision nule care schimbau digestul vechi. Preluați/revizuiți corecția metadatelor opționale și componenta de round-trip, păstrând fixture-ul/digestul original și toate câmpurile observațiilor noi. Fără calificarea ligii pe arhivă ruptă. |
| P1 | A: refactorizare de viteză | O optimizare: append/alocare, cale rapidă de rază a regelui sau biți. Păstrați ordine legală, perft, decizii, adâncime și contoare. Patru JVM-uri noi seriale; economia alocărilor nu certifică singură viteza. |
| P1 | B: mecanism recurent de pierdere | Reproduceți Larsen ulterior `f2f3` față de `Nd3` cu apărări libere la orizont comun. Identificați prima divergență de limite, acoperire sau evaluare. Componentă care eșuează la cauză, contraexemple păstrate și familii noi înghețate înainte de scorarea profesorului. |
| P1 | Partide complete auditabile | Decizie Native și indiciu Stockfish la fiecare context Alb/Negru înainte de mutare, mutarea efectivă și review final. Grupați prima pierdere WDL verificată. Prefixul neauditat nu poate fi redenumit retrospectiv observație. |
| P1 | Transfer independent, apoi Delta Elo | Separați cheia exactă, linia completă, apărările și conversia. După corectitudine folosiți partide noi; duelurile diagnostice cu poarta căzută rămân nepromovabile. Înghețați 100 de perechi noi cu culori inversate și raportați Delta Elo cu IC pe perechi 95% și bilanț/neterminate. |
| P2 | Buget adaptiv și cache reutilizabil | Brațe separate fixe/adaptive. Taxați reluări, forcing și confirmare; fixați semnalele de pericol/instabilitate înainte de holdout. Păstrați istoricul și semantica limitelor, paritate rece/cald și Basic/Advanced. Nu reveniți la 44k ca limită neexplicată. |
| P2 | Lecții reutilizabile din probleme | Precondiții generale, apărare liberă bună, contraexemple de tempo/geometrie și transfer exclus. Transpozițiile și reflexiile sunt în aceeași partiție. Memorarea FEN nu dovedește creșterea forței. |

Rădăcina dovezilor: `C:\work\sah`. Diagnosticul celor două brațe din `.studii/main-variants/RESULTS.md` a folosit reperul `f47c9b2`, anterior deschiderilor live. Brațul de viteză a redus alocarea cu aproximativ 11,4%, fără câștiguri CPU/timp consecvente. Frontiera în șah a păstrat cele șase alegeri inspectate cu mai mult lucru. Alegerea Larsen ulterioară a pierdut 465/403,5 miimi WDL la Stockfish H17/H19. Sunt controale consultate pentru investigație, fără holdout curent sau Elo nou.

Nu porniți altă calibrare mare a aceluiași live doar pentru alt rating. Această revizie nu măsoară Delta Elo nou candidat–reper. Experimentele vechi A6 cu buget mare și cazurile reparate rămân referințe, nu campania următoare implicită. [Tabelul și interpretarea rezultatului](../testing/hard-ab-elo.md#rezultat-auditabil-și-decizie)

Defectul din `training-078` este protejat prin componente ale cauzei și poarta politicii numite efective: 11/11 acceptate, fără rădăcini eșuate sau suprascriere numerică. Stockfish H17/H19 a confirmat pierdere WDL zero la cele șapte decizii Albe din PV. Mecanismul, domeniul și recepțiile aparțin [contractului corecției măsurate](../board/hard-candidates.md#corecția-măsurată-pentru-training-078), nu unui ToDo încheiat.

Proba continuărilor configurate păstrează separat `Rf1` după `…Kf8` și `Re8` după `…Kh8`, ambele la H7 încheiat (2/2). Confirmarea veche înghețată nu are Delta Elo calificat și nu este campania implicită. Un candidat numit nou cere propriul replay brut înainte de Delta Elo sau IC 95%. [Referința candidatului](../board/hard-candidates.md#confirmare-reproductibilă) și protocolul curent guvernează pasul. Rândurile Native păstrate rămân martori de diagnostic.

Selectorul public rămâne `LEGACY`. Testele nu afirmă promovarea în produs sau ΔElo nou. Nu atribuiți experimentalului Elo Native ori rezultatele altui evaluator/buget. Fără lookup ID/FEN pentru trecerea testului. Păstrați contraexemplele și FAIL-urile utile; registrul Native separat rămâne deschis.

[Contractul nucleului și verificarea](../board/hard-candidates.md)