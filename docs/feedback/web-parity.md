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

**P2 · Identitatea execuției meniurilor native.** Propagați un ID de execuție generat prin instrumentarea meniurilor Salvate/Încheiate, recepții și Extent. Observațiile focalizate actuale au trecut pe sursele și APK-ul calificate, dar le lipsește această legătură și rămân în afara calificării suplimentare stricte. Adăugați cazuri negative pentru ID lipsă sau înlocuit înaintea admiterii lor în acea poartă.

**P2 · Oracolele vechi ale istoricului nativ.** `awaitHiddenAnalysis` încă
verifică un copil de istoric per semimutare. Aliniați `analysis-prepare/restore`
și variantele compacte la cursorul comun cu o singură mutare afișată în joc.
Păstrați lista completă a Tablei de analiză și oracolul său separat. Verificați
independent indiciul/evaluarea finalizate și poziția afișată; un timeout nu
trebuie să înlocuiască diagnosticul unui oracol UI greșit.
[Cursorul curent](../../chess-core/src/main/java/ro/atf/chess/MatchHistorySelection.java)

**P2 · Pregătirea accesibilității native compacte.** Proba compactă veche se oprește la comanda Game Review vizibilă, deoarece instantaneul nodului de accesibilitate o raportează indisponibilă. Păstrați observația eșuată; actualizați și verificați nodul real înaintea apăsării, fără creșterea timeout-ului. Game Review la 360 × 640 dp și text 1,3×, precum și proba separată de rotație RO/EN, au trecut pe același APK semnat. Aceste verificări nu închid eșecul helperului vechi. Openings Web la 740 × 420 încă necesită aproximativ 37 de pixeli CSS de derulare a documentului; păstrați cazul în lucrarea pentru peisaj compact de mai sus.

[Politica comună, comenzile și verificările randate](../testing/README.md#antet-și-navigare-comune) dețin contractul implementat al antetului și navigării; păstrați aici doar criteriile de geometrie, lifecycle și intrare încă neîndeplinite.

## WEB-03 · Componente și E2E pe contractul comun

**P2 · Dovezi pereche ale adaptoarelor.** Antetul comun și cursorul unic au deja cazuri comune de componentă. Lipsește o recepție randată reutilizabilă pentru aceeași poziție sursă, variantă, cursor și secvență Înapoi pe APK și Web. Motorul Web mai vechi păstrat nu trebuie prezentat ca paritate cu un motor nativ mai nou.

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

**P2 · Focus, text mărit și contexte rămase.** Tabla de analiză compactă publicată are un meniu ancorat observat și o tablă neschimbată, fără derularea documentului. Aceasta nu încheie matricea RO/EN la 130% pentru Puzzles, Game Review și De ce.

Verificați ultima opțiune, închiderea prin tastatură, revenirea focusului și sursa/cursorul păstrate în aceste contexte, la dimensiuni compacte și desktop. Păstrați recepția driverului și capturile pentru fiecare profil lipsă; verdictul componentelor nu implică paritate randată completă. [Reperul compact observat](https://github.com/crististefanita/chess/blob/b51e17e7752cc0c0a3ec7be9500a362f4d3b20d2/release/0.16.1/reports/web-rendered-observation.json) · [Contractul meniului](../../web/README.md#meniul-principal-și-piesele)

## WEB-06 · Culorile, acțiunea de final și reutilizarea analizei

**Componentele trec; calificarea randată pereche rămâne deschisă.** Web atribuie fiecărei mutări grupul de culori din APK și arată acțiunea localizată **📊 Analiza partidei** la poziția finală. La redeschiderea unei semimutări deja analizate în aceeași partidă neschimbată se reutilizează doar analiza completă, cu dovadă. Cache-ul păstrează obiectul poziției sursă pentru identitatea liniei jucabile, resetează cursorul becului la reutilizare, reține cel mult 64 de poziții și se golește la schimbarea identificatorului partidei. Analiza incompletă și lucrul altei partide nu sunt reutilizate. Cache-ul există doar în proces; după reload poate fi necesar un nou calcul. `MatchAnalysisCacheComponentTest` și `ChessWebReviewPresentationTest` califică acum contractele cache-ului și prezentării prin aserțiuni de componentă și Extent; `NavigationGovernanceTest` verifică folosirea aceluiași contract în adaptoarele Android și Web.

`PortableSha256` rezolvă blocajul de compilare TeaVM urmărit mai sus. Bundle-ul TeaVM complet și porțile de paritate ale workerului împachetat trec acum. Cache-ul și prezentarea au încă nevoie de recepții vizuale APK/Web pereche pentru un rezultat terminat în RO/EN și reluarea istoricului; codul comun și paritatea workerului nu demonstrează singure paritatea vizuală.

- Poarta de componentă acoperă fiecare grup de clasificare, etichetele textuale, vizibilitatea doar la final, blocajele din timpul analizei, accesarea aceleiași perechi identificator/semimutare, redarea liniei cu obiectul de poziție exact, resetarea becului, evacuarea LRU la 64 de poziții, invalidarea la schimbarea partidei și respingerea lucrului incomplet.
- Închideți doar cu recepții pereche APK/Web pentru rezultatul final și lista colorată în RO/EN, plus o secvență observată Înapoi/Înainte care arată revenirea imediată la o semimutare AI memorată, fără o căutare nouă. Sursa comună singură nu dovedește paritatea randată.
