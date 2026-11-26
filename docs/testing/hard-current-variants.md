# Copii Native Hard din main: rezultat și limite

## Pe scurt

[Protocol A/B și Elo](hard-ab-elo.md) · [Feedback curent](../feedback/hard.md)

Reperul acestei runde este `65a29ab023a6a1da11a0da1b3476da717bee5baf`. Selectorul public este `LEGACY`; configurația adaptivă existentă permite 100k–200k noduri suplimentare. Acest contor nu include căutarea comună H3. Repertoriul și politicile rămân identice între părinte și fiecare copie.

## Viteză

Trei mecanisme au fost testate separat, fiecare pornind din același main. Nu sunt combinate.

| Copie | Modificare | CPU proces, cumulat | CPU fir, cumulat | Forkuri cu ambele CPU mai mici | Verdict |
|---|---|---:|---:|---|---|
| A1 | Scurtătură de legalitate în afara tuturor razelor regelui | −1,50% | −0,92% | Criteriul nu trece | Necalificată pentru viteză. |
| A2 | Identificarea pieselor țintuite la rege prin măști de biți | −2,01% | −1,98% | Criteriul nu trece | Necalificată pentru viteză. |
| A3 | Împachetarea cheii exacte din biții pătratelor ocupate | **−15,51%** | **−15,81%** | **4/4** | Calificare inițială pe lotul măsurat. |

La A3 timpul cumulat scade cu 16,46%, în toate cele patru forkuri. Alocările rămân practic identice, +0,014%; optimizarea nu este prezentată ca reducere de memorie. Fiecare probă are patru JVM-uri seriale noi, 64 de observații de încălzire și 64 măsurate, cu ordine alternată. Propriile build-uri și arbitrul Stockfish nu rulează concomitent cu măsurătorile. Sunt rezultate JVM pe un host și patru contexte de temporizare; nu reprezintă calificarea performanței Android sau un câștig Elo.

Profilul părintelui a indicat cheia de căutare în 93 dintre 501 eșantioane CPU drept primul cadru Native. A3 elimină împachetarea celor zero până la 62 de pătrate goale: parcurge `occupied`, cu `Long.numberOfTrailingZeros` și `bits &= bits - 1`. Cele patru cuvinte ale cheii rămân exacte, fără hashing probabilistic sau stări omise. Tura, rocada și en passant se păstrează. Aceasta este o refactorizare; nu adaugă o politică de joc.

`SearchKeyPackingTest` compară independent cu vechea împachetare pe toate cele 64 de pătrate, pe peste 1.800 de stări, promovări, capturi și drepturi speciale. Suitele țintite păstrează oracolul independent de legalitate, perft, cache/PVS și frontiera Stockfish. Paritatea Native compară mutarea aleasă, toate PV-urile ordonate, adâncimea, starea completă și contorul original; un număr egal de noduri singur nu este suficient.

## Confirmarea A3

Confirmarea ulterioară pe patru contexte dense, în alte patru JVM-uri seriale, a eșuat: CPU proces +8,85%, CPU fir +11,23%, timp +10,06%; numai 2/4 procese au CPU proces mai mic și 1/4 CPU fir mai mic. Cele 72 de perechi identice rămân dovezi de corectitudine, dar rezultatul inițial de viteză nu se generalizează. A3 se păstrează experimental și nu se promovează ca optimizare generală. Dovezile noi sunt în `.studii/native-hard-next/confirmation/`, sub aceeași rădăcină externă.

## Forța de joc

B încearcă o fereastră de ±200 cp pentru prima rădăcină, prezisă numai din valoarea ei exactă la iterația completă anterioară. Fail-high/fail-low impune re-căutare completă, în același buget; testele compară cu minimax independent, verifică întreruperea și resping YAML necunoscut. Veto-urile H6 rămân neschimbate.

Mecanismul **nu repară Larsen**: decizia reală rămâne `f3`, H5, 100k noduri suplimentare. Căutările izolate la același orizont arată H5 `f3` +24 cp versus `Nd3` +21 cp, dar H6 −82 versus −20 cp. Aceste probe au buget separat pe rădăcină și nu sunt decizii complete de 200k. Stockfish 19 confirmă alegerea `Nd3`; `f3` pierde 465/403,5‰ WDL la H17/H19. Exit-ul real al procesului Stockfish este păstrat.

Pe lotul exploratoriu de 24 de poziții înghețate înaintea arbitrajului, B schimbă zero mutări, câștigă H6 într-o poziție și îl pierde într-alta. Cele 48 de perechi Basic/Advanced rămân identice. Lotul provine din 12 familii cunoscute; nu este un holdout independent global și nu este o ligă de partide complete. B rămâne experimental, fără promovare.

## Dovezi și următorul pas

### Probele ulterioare la rădăcină

Fereastra îngustă pentru rădăcinile secundare păstrează scorurile și toate egalitățile în minimax independent, dar schimbă unde se termină căutarea limitată. Pe toate cele 1.238 de prime decizii publice, reperul acceptă 1.214, iar proba 1.213: nicio recuperare și o pierdere în `training-012`. Proba ajunge la H5 și preferă `…h5`; linia se termină cu `…Qxf2`, deși `Kxf2` și `Qxf2` sunt legale. La reper rămâne `…Rb1` din H4. Adâncimea mai mare nu garantează progres când frontiera este evaluată înaintea schimbului complet.

Stockfish 19 confirmă `…Rb1` la H17/H19; `…h5` pierde 502/503,5‰ WDL în comparația separată, cu replici libere. Recepția păstrează șase faze, scoruri exacte și exit-ul real al arbitrului. Proba folosește explicit originea compusă și istoricul gol; nu pretinde că este partida-sursă originală.

Pentru ambele brațe sunt acceptate primele decizii din toate cele 500/300/200 de probleme de mat în 1/2/3, din 95/100 tactici și 24/24 intermezzo; diferența este în antrenament, 95/114 → 94/114. Acestea sunt prime decizii, nu certificarea tuturor liniilor și apărărilor sau un rating Elo.

O a doua probă permite liderului H4 să revină numai după comparația H6 completă și re-verificare H4 exactă, fără credit de orizont. Repară experimental Larsen la 500k (`Nd3`) și alegerea Caro–Kann de studiu (`Rd1`). Stockfish H17/H19 confirmă aceste mutări, dar proba pierde 500/498‰ WDL în `training-021`, în ambele culori, prin `Qxf5+` în loc de `Qg7+`. Prin urmare, nici această generalizare a veto-ului nu este promovată.

`NativeHorizonGuardTest` protejează poziția și oglindirea pentru `training-012`. Fișierul `hard-lessons/visible-exchanges.yaml` este doar în resursele testelor: perechea schimb neterminat/sacrificiu de damă verificat guvernează viitoarea reparație și nu introduce mutări cunoscute în motor.

Rădăcină externă: `C:\work\sah`; dosar relativ `.studii/native-hard-current/`. `README.md` descrie planul, `RESULTS.md` verdictul, iar `publication/comparison-v2.html` păstrează Extent cu aserțiunile originale importate, expected/actual/verdict, recepții Native, faze Stockfish și controale negative. Originalele componentelor sunt în `build/<copie>/initial/components.html`. Un import în raport nu este o nouă execuție de test.

Păstrează FAIL pentru A1/A2 și obiectivul B; nu ascunde aceste verdicturi în PASS-ul componentelor. JFR-ul original se păstrează în `profile/parent.jfr`; agregarea este reproductibilă prin citire în flux. Exportul JSON redundant al stivelor a fost eliminat.

Trasarea deciziei complete Larsen confirmă două cauze succesive: oprirea adaptivă înaintea primei rădăcini H6 la 100k și veto-ul care păstrează `f3` după o comparație H6 completă la 500k, cu `Nd3` câștigător. Bugetul de 200k nu termină întreaga comparație. Probele de cost și corroborare au fost executate și respinse din motivele de mai sus. Prioritatea următoare este frontiera tactică demonstrată în `training-012`, cu sacrificiul corect drept contraexemplu, nu reluarea acelorași reglaje de rădăcină. A3 rămâne experimental după confirmarea de viteză nereușită; combinarea cu o schimbare de forță ar necesita un braț nou. `Delta Elo = NOT_MEASURED`. Stockfish Lite continuă să dețină ținta pentru toate indiciile, bara și analiza finală; APK/RAG aparțin proprietarului separat al livrării.

Verificarea de dinaintea sync-ului ulterior, pe copia main fără A3, trece 75/75 componente și controale de guvernanță. Compatibilitatea arhivei este reparată fără schimbarea digestului istoric așteptat. Rezultatele, inclusiv experimentele FAIL, sunt indexate sub `C:\work\sah`, dosar relativ `.studii/native-hard-next/`.
