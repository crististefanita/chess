# Motorul Hard experimental A

## Roluri și integrare

Nucleul `HardCandidateSearch` este un experiment explicit, separat de algoritmul public anterior. `HardMaster` adaugă istoricul partidei, cartea, politica și dovada generică de mat. [Selectorul](../../chess-core/src/main/resources/hard-runtime.yaml) rămâne `LEGACY`: nici existența codului A, nici un raport de mecanism PASS nu îl promovează în joc.

| Fișier | Responsabilitate |
|---|---|
| [Politica masterului](../../chess-core/src/main/resources/hard-master.yaml) | Reper experimental, bugete distincte pentru nucleu și certificat |
| [Politica următorului candidat](../../chess-core/src/main/resources/hard-next.yaml) | Parametri și mecanisme versionate, numai pentru comparații explicite |
| [Căutarea](../../chess-core/src/main/java/ro/atf/chess/HardCandidateSearch.java) | Alpha-beta iterativ, PVS opțional, quiescence și limite de rezultat |
| [Adaptorul](../../chess-core/src/main/java/ro/atf/chess/HardMaster.java) | Context, rezultat executabil și taxarea integrală a calculului |

Integrarea păstrează API-urile `start(setup, openingChoice)` și `openingChoice()`, checkpointul `5|`, ramificarea istoricului și repertoriul public. `TacticalAnalysis` păstrează implementarea de pe main; numai helperul existent de ordonare a capturilor este accesibil nucleului nou. Presetul A nu înlocuiește evaluarea sau bonusurile native.

## Contractul rezultatului

Scorul este din perspectiva părții la mutare. `EXACT`, `LOWER` și `UPPER` au semantici diferite: o limită nu este un scor final exact. Întreruperea păstrează numai ultima iterație completă. Dacă nu există, analiza rămâne fără scor, iar adaptorul de joc refuză fabricarea unei mutări.

Certificatul A este izolat în `HardMateCertificate`; `ForcedMateSearch` și API-ul său nativ rămân identice cu origin/main. Dovada de mat AND/OR explorează toate apărările și nu citește soluțiile puzzle-urilor, FEN-uri memorate sau ID-uri de catalog. Numai `PROVED` înlocuiește nucleul; o probă neconcludentă își păstrează costul raportat și nu consumă din plafonul declarat separat pentru căutare.

TT numeric păstrează istoricul reversibil, contorul, distanța și orizontul. O sugestie de mutare fără scor poate fi refolosită numai după reverificarea legalității. Schimbarea evaluatorului invalidează scorurile din alt scope. Nu există memorie de învățare implicită sau transfer necondiționat între partide.

Remizele automate ale aplicației la cinci repetări/75 de mutări rămân distincte de politica experimentală de revendicare la trei/50. Extragerea numai a unei FEN poate pierde această informație.


## Frontiera tactică numită

Schema 5 declară explicit `quiet-tactics`. Schemele istorice 1–4 și constructorii anteriori păstrează `NONE`; nu primesc tacit o frontieră nouă.

| Valoare YAML | Politică | Comportamentul căutării limitate |
|---|---|---|
| `none` | `NONE` | Frontiera existentă de capturi și ieșiri din șah |
| `forcing-pins` | `FORCING_PINS` | Mode 7: două șahuri fără captură, o amenințare/întărire fără captură a legării damei și răspunsurile defensive legale corespunzătoare |

`forcing-pins` cere o identitate A nouă, v6 sau mai mare. Bugetele nucleului/certificatului rămân aceleași; toate vizitele suplimentare ale frontierei sunt taxate. Adaptorul obișnuit `HardMaster` citește politica numită. Un studiu poate suprascrie explicit modul numeric, dar aceea este dovadă diagnostică, nu calificarea candidatului configurat.

`decideObserved` întoarce acțiunea și raportul unicului său calcul real. Numai o acțiune `BOOK` fără căutare poate omite raportul. O acțiune completă trebuie să corespundă exact rădăcinii PV, orizontului, costului total, motivului opririi și certificatului. Dovezile incomplete pot fi observate fără mutare, iar adaptorul obișnuit de joc le respinge. Astfel liga nu recalculează o altă căutare doar pentru a obține PV.

## Corecția măsurată pentru training-078

Candidatul numit `hard-a-v6` trece toate cele **11/11 controale materiale**, păstrând cele zece recuperări anterioare. Poarta politicii efective nu are o suprascriere numerică a frontierei: citește schema 5 cu `FORCING_PINS` și apelează `decideObserved`, astfel încât acțiunea, PV, orizontul și costul aparțin aceluiași calcul. Selectorul public rămâne `LEGACY`.

Cauza era o continuare forțată fără captură omisă: după două șahuri de turn fără captură și schimburi, un turn poate adăuga un atac legal asupra unei dame deja absolut legate. Modul 7 include această întărire limitată a bateriei și răspunsurile legale ale apărătorului. Atacurile din afara axei propriei legări a piesei atacatoare sunt respinse. Componentele includ contraexemple, cazuri de fereastră îngustă/cache/dovadă incompletă, ambele culori și transfer pe alte coloane și diagonale, verificat prin generarea mutărilor legale independent de euristica tiparului.

În `training-078`, candidatul alege `Rxe6` (`e4e6`), cu o valoare H5 încheiată de +286 în propriul model de scor. Recepția raportează `EXACT` pentru acel orizont încheiat și `NODE_LIMIT` pentru iterația ulterioară întreruptă; aceasta nu afirmă că H8 s-a încheiat sau că evaluarea șahistică este perfectă. Cele 1.003.134 de vizite totale cuprind 1.000.000 de vizite ale nucleului și 3.134 de vizite ale certificatului de mat neconcludent. PV capturată în UCI este:

```text
e4e6 c6f3 e6e7 g7h8 e7e8 c8e8 e1e8 h8g7 g4d7 f3f7 e8e7 b7d5 e7f7 d5f7
```

Stockfish 19 a verificat independent toate cele **șapte decizii ale Albului de pe această PV furnizată**, la H17/H19: fiecare a păstrat 1.000‰ WDL așteptat, cu pierdere zero față de cea mai bună mutare liberă la ambele adâncimi. Aceasta este o dovadă limitată la continuarea înregistrată, nu la o partidă completă sau la forța universală.

[Proba continuărilor](../../atf-tests/src/test/java/ro/atf/chess/Hard078ContinuationStudyTest.java) din ruta de joc configurată trece separat **2/2 contexte**, după cele patru semimutări legale ale prefixului. După `Rxe6 Qxf3 Re7+ Kf8`, A6 alege `Rf1` la H7 încheiat, cu 1.004.711 vizite totale. După `Rxe6 Qxf3 Re7+ Kh8`, alege `Re8` la H7 încheiat, cu 1.003.819 vizite totale. Ambele recepții păstrează acțiunea, PV și costul total reale, inclusiv dovada de mat neconcludentă; `NODE_LIMIT` descrie iterația ulterioară întreruptă. Recepțiile rămân în `.studii/hard-078-repair/publication-continuations/`, sub rădăcina studiilor. Aceste două continuări aprobate sunt distincte de poarta celor unsprezece rădăcini și nu stabilesc rezultatul unei partide complete.
Politica țintește H8/Q32, TT 22k, un buget de 1M pentru nucleu și maximum 100k separat pentru mat, cu plafon total de 1,1M. Următoarea comparație de forță este **A6 / FORCING_PINS contra A5 / NONE, pe 100 de perechi noi cu culorile inversate și aceleași bugete**. ΔElo și IC 95% nu se deduc din rezultatele controalelor. Catalogul complet propriu, transferul independent și porțile de conversie rămân deschise înaintea promovării în produs.

Planurile legate de sursă, deciziile și Extent rămân sub `SAH_STUDY_ROOT`, în `.studii/hard-078-repair/publication-named-gate/` și directorul `reports/` aferent. `plan.txt` din primul director înregistrează `explicitOverride=false`; `verdict.txt` înregistrează `accepted=11/11` și `failedRoots=[]`. Acestea sunt rezultate ale studiului de sursă, nu calificarea unui APK.

## Confirmare reproductibilă

[Harnessul de confirmare](../../atf-tests/src/test/java/ro/atf/chess/HardConfirmationLeague.java) folosește seedul `93714689`, zece prefixe în fiecare dintre zece familii de deschideri, exclude starturile echivalente anterioare și fixează 100 de perechi cu culorile inversate. Plafonul absolut este de 220 de semimutări. Planul amprentează clasele/resursele compilate, runnerul/statistica efective și excluderile copiate.

[Fazele studiului](../../atf-tests/src/test/java/ro/atf/chess/HardConfirmationStudyTest.java), activate explicit, sunt:

1. `freeze` fixează corpusul, identitățile și excluderile copiate înaintea primei partide a motoarelor. Fazele ulterioare resping datele de intrare schimbate.
2. `run` înregistrează o singură dată fiecare acțiune observată efectiv, inclusiv scorul `EXACT` al orizontului încheiat, PV și costul total al nucleului/certificatului. Acțiunile din carte sunt declarate separat; lipsa unui rezultat executabil rămâne explicit indisponibilă.
3. `replay` folosește snapshotul compilat înghețat pentru a reconstrui istoriile legale, PV, costurile și fiecare pereche de culori, înainte de a calcula victorii/remize/înfrângeri/neterminate, ΔElo, intervalul de 95% prin gruparea familiilor și sensibilitatea la partidele neterminate.

Cele [șapte componente de acoperire](../../atf-tests/src/test/java/ro/atf/chess/HardConfirmationCoverageTest.java) resping perechile lipsă/străine/duplicate, registrele trunchiate sau modificate, bugetele schimbate, corpusul/excluderile substituite și bytecode-ul modificat al harnessului real. Verifică și noutatea deterministă și SAN.

Intervalul bootstrap este aproximativ când există numai zece grupuri de familii. Publicați rezultate numerice de forță numai după ce atât `run`, cât și `replay` trec cu zero decizii indisponibile și păstrați sensibilitatea la partidele neterminate. Acest experiment nu afirmă auditarea completă a indiciilor/Game Review sau promovarea. Rularea pereche este în curs, deci nu afirmăm un ΔElo nou.

## Compatibilitate Android a nucleului nou

Clasele A noi păstrează contractul API26 pentru colecții: indexare obișnuită în loc de `getFirst/getLast`, `Collectors.toList()` în loc de terminalul nou al streamului și `HardCollections` pentru fabrici defensive. Listele și seturile rămân imuabile; elementele null și duplicatele seturilor sunt respinse. Nu schimbăm clasele remote pentru această integrare.

[Referința Android List](https://developer.android.com/reference/java/util/List) și [Set](https://developer.android.com/reference/java/util/Set) arată că fabricile moderne sunt API30+, iar colecțiile secvențiate sunt și mai noi. Versiunea limbajului Java nu garantează disponibilitatea acestor metode pe dispozitiv.

`HardAndroidApiTest` scanează AST-ul celor nouă clase A, fără exemplele din comentarii/șiruri, și verifică semanticile helperului. Acest control focalizat nu înlocuiește lint sau sanity Android al întregii aplicații.

## Verificare și forță

Componentele nucleului compară cu minimax independent, cu și fără cache, la ferestre înguste/completă și întrerupere. Alte componente verifică păstrarea API-urilor de pe main, a metadatelor și a rutelor Basic/Advanced/Hard când selectorul este LEGACY. Toate au raport ATF/Extent și intră în suita componentelor sau a guvernanței.

Exemplu dintr-un checkout extern deja compilat, cu JDK21 și director temporar/depozit Maven configurate în rădăcina studiilor:

```powershell
mvn -pl atf-tests test '-Dtest=HardCandidateSearchTest,HardCandidatePvsTest,HardMasterRemoteCompatibilityTest,SuiteGovernanceTest#everyJvmTestHasExactlyOnePrimarySuiteAndAnExtentAudit' "-Dchess.extent=$studyRoot/reports/master-components-new.html"
```

Folosiți o cale Extent nouă pentru fiecare execuție. Datele și rapoartele rămân în `SAH_STUDY_ROOT`, relativ la care păstrăm inventarul surselor și recepțiilor.

Rezultatele obținute într-un checkout vechi nu se atribuie acestei integrări: evaluatorul și infrastructura publică pot diferi. Forța trebuie verificată pe sursa efectivă publicată, cu aceleași starturi, culori inversate, bugete explicite și intervale de încredere. Componentele de corectitudine nu sunt o măsurătoare Elo.

[Feedback și porți de promovare](../feedback/hard.md)