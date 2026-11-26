# Progres Native Hard: pași și decizii

[Feedback deschis](../feedback/hard.md) · [Protocolul de măsurare](hard-ab-elo.md) · [Ownership Stockfish Lite](../feedback/stockfish-web.md)

## Baza de lucru

Pornește din main actual și păstrează fiecare experiment într-o copie identificată. A3 schimbă numai construirea cheii exacte din ocuparea tablei; nu schimbă bugetul, repertoriul, evaluarea sau mutările. Confirmarea vitezei și creșterea forței de joc sunt rezultate distincte. [Prima calificare A3 și experimentele respinse](hard-current-variants.md)

## Ordinea de execuție

1. **Închide defectele de audit.** O recepție veche trebuie să păstreze forma și digestul originale. Câmpurile introduse ulterior lipsesc numai dacă sunt nule; datele reale noi rămân serializate și verificabile. Reproduce înainte, repară după, păstrează ambele Extent. Nu schimba fixture-ul ca să treacă.
2. **Confirmă optimizarea înaintea integrării.** A3 a păstrat corectitudinea, dar confirmarea pe contexte dense nu a trecut; rămâne experimental. Pentru un candidat următor folosește controale dense și rare, ordine alternată și JVM-uri noi seriale. Aceleași mutări ordonate, PV-uri, adâncimi și noduri raportate trebuie să se păstreze. Nu este nevoie de o ligă Elo amplă ca să dovedești o refactorizare exactă; nici o medie favorabilă pe un singur tip de poziție nu o califică general.
3. **Localizează prima cauză a rateului de joc.** În Larsen comparația izolată H6 și decizia reală nu sunt echivalente. Înregistrează iterațiile complete/incomplete, costul fiecărei rădăcini, tipul scorului, replica liberă și motivul opririi. Observarea unui scor mai bun într-o pereche forțată nu demonstrează că decizia completă îl poate adopta.
4. **Testează un singur mecanism general într-un copil de forță.** Componente pentru cauza găsită și contraexemple, apoi familii excluse și conversie. Păstrează veto-urile justificate până când o dovadă contextuală le poate înlocui corect. PVS există deja; o opțiune deja activă nu este o îmbunătățire nouă.
5. **Antrenează și verifică prin jocuri/puzzle-uri.** Primul declin WDL din fiecare partidă devine o poziție de studiu, cu apărări și contraexemple. Separă partida-sursă, transpozițiile și oglindirile într-o singură partiție. YAML păstrează lecția și condițiile generale, nu un FEN-to-move override creditat ca învățare.
6. **Măsoară Delta Elo.** Un candidat legal poate primi un pilot diagnostic înaintea promovării; un FAIL tactic rămâne vizibil. Promovarea cere porțile convenite și confirmare pe 100 de perechi noi. Reține IC 95% pe perechi, W/D/L și neterminate. Nicio partidă nouă încheiată produce `NOT_MEASURED`, niciodată zero Elo fabricat.

După trei mecanisme nereușite, revizuiește cauza și familia de pierderi; nu continua aceeași variație de ordonare cu alte nume. O reglare de buget este un experiment separat de o schimbare a algoritmului. Bugetele sunt configurabile; adâncimea nu se reduce pentru a cosmetiza timpul.

## Cele două direcții după A3

| Copil | Obiectiv | Dovadă înaintea promovării |
|---|---|---|
| Viteză | Profil nou al A3; optimizarea unui singur cost real | Oracol, paritate și CPU măsurat. Exemple de ipoteze, nu promisiuni: cheie incrementală, copierea pozițiilor, alocările generării. |
| Forță | Repararea mecanismului confirmat în decizia completă | Minimax și limite corecte, replici libere, martori, transfer și rezultate complete, apoi Delta Elo propriu. |

Măsoară fiecare copil față de aceeași bază. Combinarea lor este un nou braț identificat; nu atribui îmbunătățirea bugetului unui algoritm și nu schimba repertoriul pe ascuns.

## Antrenorul și jurnalul de învățare

Stockfish Lite deține ținta pentru fiecare indiciu și evaluare calculată: joc AI, analiză manuală/locală, deschideri, explicații de puzzle, bară și analiză finală, pe Web/APK. Oponentul Native este separat. Integrarea și lipsurile platformelor sunt în [feedback-ul propriu](../feedback/stockfish-web.md), iar APK/RAG aparțin proprietarului livrării.

La fiecare mutare efectivă alb/negru salvează observația Native reală și indiciul Lite anterior mutării, cu istoricul complet și identitatea motorului. La final păstrează analiza completă. Arbitrul offline separat compară libera alegere și mutarea jucată la același orizont; nu substituie decizia Hard și nu reconstruiește retroactiv observații lipsă.

Pentru o poziție compusă, păstrează explicit FEN-ul de origine și mutările ulterioare. Un checkpoint de partidă construită artificial nu este suficient ca dovadă a originii. Verifică poziția Native restaurată înaintea arbitrajului; refuză perechile cu alte origini. În această rundă prima probă `training-012` a fost invalidată tocmai de acest control și refăcută cu origine explicită, fără a contabiliza tentativa invalidă ca rezultat de joc.

## Feedback și dovezi curate

Feedback-ul conține numai lipsuri actuale și criterii de închidere. După verificare, mută comportamentul durabil și lecția în documentul tehnic potrivit; păstrează legătura către dovadă și elimină ToDo-ul rezolvat. Un experiment respins rămâne documentat ca rezultat, fără să domine următoarea rundă.

Toate build-urile și dovezile sunt externe, sub `C:\work\sah`, cu căi relative la această rădăcină. În dosarul studiului păstrează un index, Extent cu aserțiunile originale, PGN/jurnalul necesar și numai logurile care aduc informație nereprezentată în raport. Un raport importat nu este o nouă execuție de test.
