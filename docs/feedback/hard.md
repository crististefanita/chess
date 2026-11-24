# Feedback Hard · nucleu experimental

## Priorități

| Prioritate | Caz | Dovadă necesară |
|---|---|---|
| P1 | Catalogul candidatului și transferul independent | A6 păstrează toate cele 11 controale materiale, dar acel lot de studiu consumat nu este catalogul complet sau dovada de forță pe familii noi. Rulați catalogul propriu, regresia etapelor acceptate, familii independente și conversia completă cu replici adverse libere. |
| P1 | Confirmarea forței la buget egal | Comparați A6 / FORCING_PINS cu A5 / NONE pe 100 de perechi noi cu culori inversate, ambele H8/Q32, TT 22k, nucleu 1M și maximum 100k vizite de mat. Raportați ΔElo cu IC 95%, neterminate explicite și costul fiecărui braț; nu moșteniți o estimare veche cu bugete inegale. |
| P2 | Compararea rutei native | Observabilitate independentă pentru vizite și limite, cu paritate de decizie/adâncime înaintea duelurilor; nu modificați algoritmul legacy pentru a-l alinia candidatului. |

Defectul de rădăcină/frontieră din `training-078` este acum protejat prin componente ale cauzei și poarta politicii numite efective: 11/11 acceptate, nicio rădăcină eșuată și nicio suprascriere numerică. Stockfish H17/H19 a găsit pierdere WDL zero pentru Alb la toate cele șapte decizii ale Albului din PV furnizată. Mecanismul durabil, domeniul dovezii și recepțiile aparțin [contractului corecției măsurate](../board/hard-candidates.md#corecția-măsurată-pentru-training-078), nu unui rând ToDo încheiat.

Proba continuărilor din ruta de joc configurată păstrează separat `Rf1` după `…Kf8` și `Re8` după `…Kh8`, ambele la H7 încheiat (2/2 contexte). Confirmarea înghețată pe 100 de perechi este în curs; replay-ul perechilor brute este necesar înainte de raportarea ΔElo sau a intervalului său de 95%. Consultați [protocolul de confirmare](../board/hard-candidates.md#confirmare-reproductibilă). Cele 29 de rânduri existente de feedback nativ rămân neschimbate în indexul lor inițial.

Selectorul public rămâne `LEGACY`. Aceste teste nu afirmă promovarea în produs sau un ΔElo nou. Nu atribuiți motorului experimental Elo-ul nativ sau rezultatele unui candidat cu alt evaluator/buget. Nu adăugați lookup de ID/FEN pentru a trece un test. Păstrați contraexemplele și rapoartele FAIL utile; registrul separat de feedback nativ rămâne deschis.

[Contractul nucleului și testarea](../board/hard-candidates.md)