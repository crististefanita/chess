# Copiile de continuare Hard: progres măsurat și porți respinse

[Priorități Hard](../feedback/hard.md) · [Calibrarea profilurilor curente](current-profile-calibration.md) · [Protocol A/B](hard-ab-elo.md)

## Pe scurt

Șase copii, inclusiv reperul, pornesc din main `cda04af9388707b3533d5cb487e5f2ccbfa8e5f5`. Lotul de descoperire conține **26 de exerciții materiale consumate** unde reperul acceptă prima cheie, dar ulterior părăsește traseul scris. Nu este lot independent nou. Fiecare copie joacă și toate cele 1.238 de trasee scrise, fără soluție înaintea deciziei efective.

Niciun oponent experimental nu este promovat. Acordul primei mutări, traseul complet, WDL estimat independent, conversia până la rezultat și Delta Elo în perechi sunt rezultate diferite. Păstrarea unui șir sursă nu dovedește șah bun; nici repararea a zece șiruri alese nu dovedește forță generală.

## Intervenții separate

| Copie | Schimbare față de main | Etape țintă recuperate / 26 | Prime chei / 1.238 | Trasee complete / 1.238 | Trasee integrale câștigate / pierdute |
|---|---|---:|---:|---:|---|
| Reper | Adaptiv existent 100k–200k, H6 obișnuit | 0 | 1.214 | 1.188 | 0 / 0 |
| Buget | Adaptiv 200k–400k, H6 obișnuit | 0 | 1.212 | 1.186 | 0 / 2 |
| H8 | Același 200k–400k, H8 obișnuit | 10 | 1.218 | 1.188 | 12 / 12 |
| Reutilizarea frontierei | Bugetul reperului; activează reutilizarea existentă exact/limită/mutare preferată din prima iterație | 0 | 1.213 | 1.187 | 0 / 1 |
| H7 | Același 200k–400k, H7 obișnuit | 7 | 1.212 | 1.180 | 8 / 16 |
| Rejucare selectivă | Păstrează căutarea originală și poarta H6. Reverifică liderii care nu dau șah mai profund; ambele căutări consumă un plafon total de 500k | 6 | 1.222 | 1.190 | 8 / 6 |

Toate copiile păstrează Basic și Advanced exact pe cele 26 de contexte: 312 comparații de profil între cele șase variante, inclusiv PV ordonat, adâncime și lucru taxat. Contorul public rămâne **lucru suplimentar fără H3 comun**. Plafonul transpozițiilor rămâne 50k. Creșterea bugetului nu este îmbunătățire de algoritm.

Copilul H8 extinde explicit limita H7 a parserului doar în acea copie. Preflight-ul inițial necalificat și eroarea de inlining a constantei Java sunt păstrate separat; politicile efectiv încărcate sunt verificate înaintea înghețării comune. H7 și rejucarea selectivă sunt ablații de descoperire ulterioare, înghețate separat și legate de acel părinte. Nu sunt confirmări independente.

Schimbarea orizontului terminal configurat schimbă și locul unde acționează veto-ul liderului terminal. Unele recuperări H8 termină deja la H6/H7. Nu atribui câștigurile doar atingerii H8. Rejucarea selectivă studiază separat o regulă de siguranță; păstrează liderii părinte care dau șah, deci nu poate repara anumite recomandări greșite care dau șah.

## Comparația Stockfish și corecția ceasului

Stockfish 19 desktop fixat, fără limitare de forță, compară jocul liber, alegerile Native efective și referința scrisă la H17/H19 complete. Fiecare fază pornește rece, cu un fir, hash 32 MiB și plafon de siguranță declarat de zece milioane de noduri. Orizontul neterminat nu furnizează scor final. Profesorul Lite al produsului este furnizor separat.

Șase comparații inițiale au folosit ceasul nenul al originii din catalog, în timp ce partida Native compusă își resetase ceasul liniștit. Grupurile sunt înlocuite și păstrate. Fazele corectate folosesc istoricul și ceasul Native efectiv; componentele resping originea necorectată. Studiul păstrează **256 de faze raw ale profesorului**, inclusiv probele înlocuite. Comparațiile țintă calificate folosesc 172 de faze; investigația separată de regresie folosește 48. Fixture-ul portabil țintă păstrează cele 156 de faze liber/reper/referință necesare celor 26 de cazuri.

| Copie | Pierderi WDL estimate mari la ambele orizonturi / 26 | Pierdere medie H19, ‰ | Noduri suplimentare efective medii |
|---|---:|---:|---:|
| Reper | 16 | 495,85 | 79.768 |
| Buget | 16 | 495,85 | 104.483 |
| H8 | 8 | 210,54 | 229.880 |
| Reutilizarea frontierei | 16 | 495,85 | 59.312 |
| H7 | 11 | 322,83 | 173.091 |
| Rejucare selectivă | 12 | 361,88 | 302.160 |

„Mare” înseamnă pierdere de minimum 150‰ la ambele orizonturi complete. Sunt estimări Stockfish pe poziții consumate, nu dovezi tablebase sau partide convertite. H8 are un semnal local real, dar pierde martori protejați și douăsprezece trasee complete în altă parte. Consumul mai mic al variantei de frontieră nu califică optimizarea cât pierde invazia turnului. Timpii concurenți nu stabilesc performanță serială controlată.

## Regresie reală și referință curriculară nepotrivită

Varianta selectivă trece cele douăsprezece metode de componentă existente, păstrează toate primele chei acceptate și câștigă opt chei noi. Cele șase trasee integrale pierdute au semnificații diferite:

| Caz | Rezultat H17/H19 independent | Consecință |
|---|---|---|
| training-056 | Rxc7 al candidatului estimează remiză; Nxc7 al părintelui păstrează câștigul | Regresie reală; protejează continuarea efectivă acceptată în componente. |
| training-091 | Qh2 al candidatului pierde cel puțin 520‰; Rf8 păstrează câștigul | Regresie reală; verifică apărarea liberă și frontiera încheiată. |
| training-096 | Nd5 al candidatului estimează înfrângere; Rh4 păstrează câștigul | Regresie reală; câștigurile locale țintă nu o pot șterge. |
| tactic-003, training-040 | Mutare diferită de traseul scris, aceeași estimare WDL câștigătoare la ambele orizonturi | Nepotrivire cu traseul strict, nu degradare demonstrată de joc. Sunt încă necesare conversia și o rută alternativă explicit verificată. |
| training-060 | Vechiul Kxg2 de referință estimează înfrângere; e4 al candidatului păstrează câștigul | Referința coruptă nu poate deveni răspuns etalon. |

[Răspunsul original Lichess 0470w](https://lichess.org/api/puzzle/0470w) confirmă `Kxf2 b5 e4 b6 e3 b7 e2`. Rejucarea independentă chess.js a PGN-ului sursă de 71 de semimutări reproduce exact FEN-ul catalogului. Proiectul înlocuise continuarea sursă cu Kxg2 lacom. Sursa raw și rejucarea independentă sunt păstrate în resursele de test.

Nu substitui pur și simplu o linie de șapte semimutări și nu slăbi cerința existentă de +200 material închis: sursa este cursă de promovare, iar promovările vizibile inversează temporar balanța materială. Selecția trebuie să verifice câștigul complet compatibil cu obiectivul sau să susțină explicit un obiectiv de poziție câștigătoare, păstrând ID-ul, progresul salvat, indiciile și explicația finală. Până atunci, exclude referința Kxg2 greșită din concluziile de forță. Catalogul original rămâne neschimbat în acest studiu; defectul de selecție publicat rămâne obiectiv de calitate explicit eșuat.

`HardLateContinuationTest` izolează toate cele 26 de contexte și respinge execuția, etapa, ceasul, WDL-ul și plafonul schimbate. `NativeContinuationRouteTest` cheamă efectiv Native pentru cei trei martori cu trasee validate independent. `MaterialRegressionTest` separă cele trei regresii reale, cele două alternative sănătoase și referința perdantă; rejoacă și sursa primară și verifică ambele invazii protejate de turn. Niciun test nu obligă motorul viitor să repete o alegere proastă salvată. Fixture-urile sunt cunoștințe exclusiv de test, nu suprascrieri FEN–mutare în runtime.

## Următoarea intervenție și promovare

Păstrează rejucarea selectivă drept cel mai util **candidat necalificat de integrare**: +8 prime chei și +2 trasee complete, dar trei regresii de joc confirmate. Izolează prima evaluare greșită din training-056/091/096 cu replici libere la orizont comun înaintea altui buget global sau a altei poziții memorate. Păstrează recuperările și martorii originali de turn/damă. Tratarea separată a părintelui care dă șah este ipoteză limitată, nu teoremă de corectitudine.

Repară separat nepotrivirea sursă/obiectiv din training-060 pornind din sursa primară. Auditează un eșantion de alte referințe materiale înainte să le admiți ca ținte de învățare. YAML-ul tiparelor include premise, apărare optimă, contraexemple, identitatea sursei și transfer independent; potrivirea unui exercițiu înghețat nu este învățare autonomă.

Toate cele cinci porți experimentale de promovare eșuează. Nu a început o campanie nouă de conversie pe familii independente sau duel pereche: **Delta Elo = NOT_MEASURED**. După ce un candidat identificat prin sursă trece regresiile reale și se rezolvă admiterea catalogului, îngheață partide/familii sursă excluse și rulează propria comparație pereche. Tabelul Elo anterior al celor trei profiluri rămâne reper, nu valoare moștenită de vreun copil.

Planul distinge informația exactă/limitată din [implementarea căutării Stockfish](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/search.cpp) și testarea pereche din [metodologia statistică Fishtest](https://official-stockfish.github.io/docs/fishtest-wiki/Fishtest-Mathematics.html). Sursele justifică verificarea mecanismelor și păstrarea dependenței perechilor; nu certifică implementarea locală și nu prezic câștigul.

Rădăcină dovezi: `C:\work\sah`; colecția relativă curentă `.studii/hard-continuations/`. README-ul indexează Extent ATF original, copiile înghețate, registrele Native, fazele corectate ale profesorului, clasificarea regresiilor și promovarea explicit blocată. Nu se pretinde calificare Android, browser, APK sau release.
