# Web · Paritate cu APK-ul Chess Lab

## Pe scurt

Registrul urmărește diferențele rămase între APK și Web.
[Motor și adaptoare](../../web/README.md#motor-comun-și-adaptoare-de-platformă) ·
[Matricea APK–Web](../../web/parity.md)

## Ce preluăm din J4

Card Table Browser 0.2.0 și JAR-ul său de teste oferă contracte pentru
masa de cărți. O extracție J4/Chess are sens pentru un utilitar independent
de joc, dacă proba identifică un consumator în fiecare proiect, un API
stabil și codul duplicat care poate fi eliminat. Candidații sunt
transportul cu revizii și colectarea dovezilor din browser.
[Pachetele Card Table Browser 0.2.0](https://github.com/crististefanita/atf_j4/tree/3d2b7b11eedd6aadd48a82fd7858ef195e21f12e/atf-ui-browser/lib)

## WEB-01 · Motor și contract de aplicație

**P1 · Responsabil Chess.** Extrageți treptat tranzițiile de sesiune
care necesită decizii echivalente în `ChessActivity`, `AnalysisActivity`
și adaptorul Web. Începeți cu reluarea, anularea și primirea unei replici
AI întârziate. [Motor și proiecție](../testing/README.md#motor-și-proiecție)

**Rezolvat la 22.11.2026 · Compilare Web · Digestul dovezii Hard.** `PortableSha256` produce aceiași octeți SHA-256 standard fără căutare de furnizori JCA. `PawnPushProof` și `GameReviewCodec` îl folosesc, păstrând identificatorii existenți în hex minuscul și Base64. `PortableSha256Test` acoperă vectori standard, limite de padding, paritatea cu JDK-ul și ambele contracte de identificatori persistenți. `TeaVmPortabilityGovernanceTest` respinge căutarea furnizorilor sau a claselor prin class-loader în sursele nucleului de producție. Buildul TeaVM complet și testul de paritate al workerului împachetat rămân poarta de calificare; compilarea Java singură nu este suficientă.

**Verificări de închidere**

- Pe JVM și în worker, folosiți același caz cu ceas și stocare simulate.
  Verificați poziția, revizia, acțiunile permise și respingerea replicilor expirate.
- În sanity-ul nativ, verificați o singură reluare și ordinea afișată.
- Pe mobil, măsurați separat pornirea la rece și timpul workerului până la prima
  semimutare de analiză, cu același buget. Verificați anularea și schimbarea slotului în timpul calculului.

<details>
<summary>Anunțuri și focus la cititorul de ecran</summary>

**Game Review · Web.** NVDA 2026.3.1 a reprodus în RO dublarea poziției și a scorului în ediția anterioară. Graficul folosește acum o singură valoare accesibilă, cu poziție și scor. Componenta acoperă actualizările repetate, iar Extent respinge o recepție Web cu descrierea duplicată. Confirmați vorbirea pe build-ul corectat în RO și EN. Păstrați focusul pe grafic între două semimutări cu scoruri diferite și verificați revenirea la declanșator după **De ce?**. [Cititorul de ecran](../../web/README.md#cititorul-de-ecran-în-analiza-partidei)

</details>

## WEB-02 · Adaptoare și afișaj comun

**P2 · Folosirea mai bună a lățimii peisaj compacte.** La 740 × 420 dp și
font 1.3, sursa și redarea matului măsoară ambele 285 pixeli nativi la
320 dpi (142,5 dp). Navigarea încape fără derularea paginii, dar stiva
verticală lasă multă lățime nefolosită. Explorați o așezare peisaj comună,
cu explicația lângă tablă, păstrând dimensiunea sursă/redare și comenzile
de 48 dp. Cereți dreptunghiuri în componente RO/EN și capturi native înainte
de schimbarea contractului actual fără salturi.
[Controalele explicației](../../atf-tests/src/test/java/ro/atf/chess/PuzzleExplainLayoutTest.java)

**Implementat · Identitatea execuției meniurilor native; proba semnată rămâne de verificat.** Runnerul Salvate/Încheiate generează acum un ID comun pentru fazele pereche și ID-uri distincte pentru execuțiile izolate. Extent verifică marcajul nativ, recepția de execuție și faza pereche; componentele negative resping ID-uri lipsă, înlocuite sau diferite între faze. Observațiile anterioare rămân în afara calificării suplimentare stricte până la o recepție nouă a APK-ului semnat.

**Închis · Oracolul nativ al istoricului pe APK-ul semnat.**
`awaitHiddenAnalysis` compară eticheta unică a mutării selectate din partida cu AI
cu `MatchHistorySelection`; tabla manuală de analiză păstrează oracolul listei
complete. O etichetă greșită raportează separat valoarea așteptată și cea observată,
fără a o confunda cu pregătirea indiciului. APK-ul semnat 0.16.2 a trecut cinci
aserțiuni înainte și două după un restart real, cu același ID și aceeași sursă.
[Extent și execuții](../../release/0.16.2/reports/README.md)
[Cursorul curent](../../chess-core/src/main/java/ro/atf/chess/MatchHistorySelection.java)

**P2 · Pregătirea accesibilității native compacte.** Proba compactă veche se oprește la comanda Game Review vizibilă, deoarece instantaneul nodului de accesibilitate o raportează indisponibilă. Păstrați observația eșuată; actualizați și verificați nodul real înaintea apăsării, fără creșterea timeout-ului. Game Review la 360 × 640 dp și text 1,3×, precum și proba separată de rotație RO/EN, au trecut pe același APK semnat. Aceste verificări nu închid eșecul helperului vechi.

**Închisă depășirea Web Deschideri la 740 × 420, EN/RO cu text 130%.** Înainte, documentul măsura 447 px în înălțime, iar tabla 153 px. Așezarea alăturată încape acum în 420 px și oferă un pătrat de 257 px, la cel mult 2 px de modelul nativ compact. `OpeningLessonTest` și `BoardSizingGovernanceTest` leagă pragul și spațiul celor două etichete; `ChessWebOpeningLandscapePlaywrightE2e` verifică documentul randat și tabla la Următoarea/Anterioara, cu capturi în Extent. Se închide numai defectul de derulare Web. Folosirea lățimii de către sursa/redarea matului, descrisă mai sus, și captura randată pereche a APK-ului semnat rămân deschise.

[Politica comună, comenzile și verificările randate](../testing/README.md#antet-și-navigare-comune) dețin contractul implementat al antetului și navigării; păstrați aici doar criteriile de geometrie, lifecycle și intrare încă neîndeplinite.

## WEB-03 · Componente și E2E pe contractul comun

**Verificat local la 22.11.2026 · Fluxul browser Salvate/Încheiate.** O probă Chromium reală a găsit că deschiderea unei partide migrate cu AI în Salvate ridica `ReferenceError: levels is not defined` și lăsa lista goală. Rândul citește acum nivelul din textele localizate ale meniului. O componentă fără browser verifică toate cele trei niveluri în RO/EN și respinge un nivel invalid. Proba Playwright cu un singur flux și raport Extent verifică separarea listelor, matul, reîncărcarea, încadrarea tablei arhivate, istoricul și geometria tablei de analiză; imaginile și acțiunile sunt incluse în raport. Acest control închide fluxul Web Salvate/Încheiate pentru profilul compact EN testat, fără a închide matricea de randare APK/Web la 130% de mai jos. [Strategia de testare](../testing/README.md#partide-salvate-arhivă-și-continuare)

**Limita distribuției.** Chess consumă perechile JAR/POM publicate J0 Reporting și Test Kit. `atf-ui` și `atf-debug-ui` din J1 sunt module sursă; copierea unui JAR necalificat în Chess ar ascunde proveniența. Dacă după această probă locală este nevoie de un adaptor browser reutilizabil, cerem proprietarului J1 un pachet binar versionat și acceptare la consumator; un contract neutru Reporting defect se raportează separat proprietarului J0, cu raportul eșuat și coordonata exactă.

**P2 · Dovezi pereche ale adaptoarelor.** Antetul comun și cursorul unic au deja cazuri comune de componentă. Lipsește o recepție randată reutilizabilă pentru aceeași poziție sursă, variantă, cursor și secvență Înapoi pe APK și Web. Motorul Web mai vechi păstrat nu trebuie prezentat ca paritate cu un motor nativ mai nou.

**P2 · Locurile și capturile randate.** Identitatea comună a adversarului,
proiecția capturilor, desenatorul Web și rândurile fixe trec verificările JVM,
DOM simulat și worker împachetat, inclusiv istoric, întoarcere și reluare.
Secvența legală a capturilor la semimutările 13–16 se află în
`match-capture-rows.json`; contractele și numele testelor sunt în
[ghidul partidei cu AI](../../web/README.md#partida-cu-ai) și
[strategia de testare](../testing/README.md#analiza-în-partida-cu-ai).
[Cererea de publicare](../../web/publication.request) identifică pachetul
calificat; identitatea octeților și aserțiunile componentelor nu dovedesc paritatea vizuală.

**Tablă de analiză și liste, 2026-11-22.** Tabla de studiu proiectează acum
aceleași rânduri de capturi ca partida cu AI, la semimutarea selectată, în ambele
adaptoare. Cele două meniuri de câte zece intrări folosesc contractul comun
`MatchStore` și `MatchListEntry`; componentele trec prin a unsprezecea partidă
activă și a unsprezecea încheiere după serializare. Recepția randată pereche
APK/Web rămâne deschisă pentru încadrarea rândurilor compacte și largi, inclusiv
istoricul și orientarea studiului. Redarea unui indiciu separat pe Web ascunde
intenționat capturile studiului, ca să nu le atribuie altei poziții.

Închideți doar cu capturi pereche din APK-ul calificat și pachetul Web servit,
la text 130% în fereastră compactă și într-o fereastră largă. Verificați fiecare
nume, nivel, piesă capturată, margine a tablei și meniu, fără decupare sau
derularea paginii. Legați ambele capturi de identitățile sursei și artefactului.
Browserul MCP eșuează în prezent înainte de deschiderea filei, deci recepția
randată rămâne deschisă.

- Folosiți cazurile de istoric al deschiderii și analiză a matului din [contractul comun de testare](../testing/README.md#antet-și-navigare-comune).
- Păstrați separat pentru fiecare adaptor identitățile surselor/artefactelor, profilul RO/EN, starea așteptată și întoarcerea observată. Workerul și geometria nu înlocuiesc randarea nativă sau MCP lipsă.
- Închideți numai când recepțiile pereche arată aceeași linie și stare restaurată, inclusiv după reload/restart, pe APK-ul calificat și bundle-ul Web servit.

## WEB-04 · Calificare și publicare

**P2 · Calificarea ediției.** Legați în RAG commitul, cataloagele și amprentele APK și Web de recepțiile aceleiași ediții. [Criteriul de actualizare](../testing/README.md#actualizarea-pachetului-web)

**Criteriu de închidere.** Porniți proba din **Joacă în browser**, sub
APK în README, și confirmați manifestul pachetului servit. Verificați
la upgrade biblioteca de partide, o analiză arhivată și semimutarea de
reluare, inclusiv după reîncărcare și într-o filă deja deschisă.
[Upgrade Web](../../web/README.md#explicațiile-și-progresul-la-actualizare) ·
[Procesul de release](../release/README.md#calificare-și-publicare)

## WEB-05 · Meniuri și opțiuni ancorate

**Închis · alinierea titlului Analiza partidei pe Web compact.** Titlul din captura în română era centrat deoarece pagina flex permitea antetului să se micșoreze până la butonul Înapoi și titlu. Regula de lățime completă, limitată la acest ecran, păstrează titlul lângă Înapoi. La 390 pixeli CSS și text 130%, recepția randată măsoară antetul de 390 pixeli și titlul începând la 70 pixeli; antetul nativ păstrează marginea inițială de 16 dp. `NavigationGovernanceTest` și fluxul de focus în browser păzesc contractul adaptoarelor și limitele reale. Se închide doar alinierea titlului, nu și matricea de meniu și focus de mai jos.

**P2 · Focus, text mărit și contexte rămase.** Tabla de analiză compactă publicată are un meniu ancorat observat și o tablă neschimbată, fără derularea documentului. Aceasta nu încheie matricea RO/EN la 130% pentru Puzzles, Game Review și De ce.

Verificați ultima opțiune, închiderea prin tastatură, revenirea focusului și sursa/cursorul păstrate în aceste contexte, la dimensiuni compacte și desktop. Păstrați recepția driverului și capturile pentru fiecare profil lipsă; verdictul componentelor nu implică paritate randată completă. [Reperul compact observat](https://github.com/crististefanita/chess/blob/b51e17e7752cc0c0a3ec7be9500a362f4d3b20d2/release/0.16.1/reports/web-rendered-observation.json) · [Contractul meniului](../../web/README.md#meniul-principal-și-piesele)

## WEB-06 · Culorile, acțiunea de final și reutilizarea analizei

**Componentele trec; calificarea randată pereche rămâne deschisă.** Web atribuie fiecărei mutări grupul de culori din APK și arată acțiunea localizată **📊 Analiza partidei** la poziția finală. La redeschiderea unei semimutări deja analizate în aceeași partidă neschimbată se reutilizează doar analiza completă, cu dovadă. Cache-ul păstrează obiectul poziției sursă pentru identitatea liniei jucabile, resetează cursorul becului la reutilizare, reține cel mult 64 de poziții și se golește la schimbarea identificatorului partidei. Analiza incompletă și lucrul altei partide nu sunt reutilizate. Cache-ul există doar în proces; după reload poate fi necesar un nou calcul. `MatchAnalysisCacheComponentTest` și `ChessWebReviewPresentationTest` califică acum contractele cache-ului și prezentării prin aserțiuni de componentă și Extent; `NavigationGovernanceTest` verifică folosirea aceluiași contract în adaptoarele Android și Web.

`PortableSha256` rezolvă blocajul de compilare TeaVM urmărit mai sus. Bundle-ul TeaVM complet și porțile de paritate ale workerului împachetat trec acum. Cache-ul și prezentarea au încă nevoie de recepții vizuale APK/Web pereche pentru un rezultat terminat în RO/EN și reluarea istoricului; codul comun și paritatea workerului nu demonstrează singure paritatea vizuală.

- Poarta de componentă acoperă fiecare grup de clasificare, etichetele textuale, vizibilitatea doar la final, blocajele din timpul analizei, accesarea aceleiași perechi identificator/semimutare, redarea liniei cu obiectul de poziție exact, resetarea becului, evacuarea LRU la 64 de poziții, invalidarea la schimbarea partidei și respingerea lucrului incomplet.
- Închideți doar cu recepții pereche APK/Web pentru rezultatul final și lista colorată în RO/EN, plus o secvență observată Înapoi/Înainte care arată revenirea imediată la o semimutare AI memorată, fără o căutare nouă. Sursa comună singură nu dovedește paritatea randată.
