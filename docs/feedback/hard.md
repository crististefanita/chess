# Hard · priorități deschise

Selectorul public este `LEGACY`. A3 rămâne experimental: câștigul CPU inițial de 15,5% nu se confirmă pe contexte dense, unde CPU crește cu 8,85%. Nu este promovat ca optimizare generală și nu are creștere Elo demonstrată. [Rezultatele și limitele](../testing/hard-current-variants.md) · [Ordinea pașilor](../testing/hard-progress.md) · [Protocol A/B și Elo](../testing/hard-ab-elo.md)

## Ce rezolvăm acum

| Prioritate | Pas deschis | Condiție de închidere |
|---|---|---|
| P1 | HARD-03 · frontiera tactică înaintea altui reglaj la rădăcină | Proba mai ieftină pierde `training-012`: trece de la `…Rb1` H4 la `…h5` H5, cu `…Qxf2` la final și recapturi legale neincluse. Corroborarea H4/H6 rezolvă Larsen, dar pierde 500/498‰ WDL în `training-021`, în ambele culori. Repară evaluarea schimbului/replicii în componentă, cu sacrificiul corect drept contraexemplu; păstrează veto-ul și catalogul. |
| P1 | HARD-04 · jocuri complet auditabile | La fiecare mutare alb/negru: decizia Native reală, indiciul Stockfish Lite dinaintea mutării, istoricul complet și analiza finală. Compatibilitatea arhivei și răspunsurile întârziate sunt verificate. Nu redenumi jurnalul Native vechi drept Stockfish. |
| P1 | HARD-05 · transfer și Delta Elo | Familii excluse și conversie până la rezultat. Candidat legal: pilot diagnostic posibil; promovare numai după porți și confirmare pe 100 de perechi noi, cu IC 95% pe perechi și neterminate separate. |
| P2 | HARD-06 · următoarea optimizare de viteză | Investighează de ce împachetarea rară pierde pe contexte dense. Profilează și testează o singură ipoteză, eventual alegerea după densitate ori alt cost real. Păstrează A3 drept martor experimental; cere controale dense și rare înaintea promovării. |
| P2 | HARD-07 · lecții care se transferă | Din primele pierderi WDL confirmate extrage familii de puzzle-uri YAML: condiții generale, apărare optimă, contraexemplu și cazuri excluse. Măsoară prima mutare, linia întreagă și conversia separat. Memorarea unui FEN nu este progres de algoritm. |

## Ownership și limite

Stockfish **Lite** este ținta pentru toate indiciile calculate, bara evaluării, deschideri, tabla manuală, explicațiile calculate și Game Review, pe Web și Android. [Găurile de integrare și testele necesare](stockfish-web.md) se gestionează acolo. APK/RAG și publicarea aparțin celuilalt AI. Basic/Advanced/Hard rămân oponenți Native.

Înghețarea sursei, reperul efectiv, bugetul YAML, cache-ul, repartizarea familiilor și regulile de audit sunt parte din [protocol](../testing/hard-ab-elo.md), nu ToDo-uri repetitive. Compară separat algoritmul, bugetul și repertoriul. Păstrează adâncimea și toate replicile cerute; timpul este o măsurătoare, nu un motiv de a slăbi jocul.

A1/A2, frontiera cu evaziuni și aspirația nereușită sunt rezultate de studiu, nu priorități active. Reparațiile deja protejate pentru `training-078` și continuările sale sunt în [contractul tehnic](../board/hard-candidates.md#measured-repair-of-training-078). Nu le redeschide ca sarcini fără o regresie nouă.

Nici fereastra îngustă, nici corroborarea generală H4/H6 nu sunt promovate. Catalogul complet de 1.238 de prime decizii scade 1.214 → 1.213 în prima probă. Stockfish H17/H19 confirmă pierderea din `training-012`. `NativeHorizonGuardTest` păstrează tactica în ambele culori și perechea YAML schimb neterminat/sacrificiu corect. Rezultatele complete sunt în [dosarul tehnic](../testing/hard-current-variants.md); nu relansa aceeași strategie cu alte praguri înaintea cauzei de frontieră.

Compatibilitatea metadatelor opționale a trecut regresia originală și verificările noi: [contractul arhivei](../testing/hard-audit.md#optional-decision-metadata-and-old-receipts). Nu mai este ToDo; nu echivalează cu implementarea jurnalului Lite.

Dovezi externe: rădăcină `C:\work\sah`, dosare relative `.studii/native-hard-current/` și `.studii/native-hard-next/`. Fără partide noi încheiate: `Delta Elo = NOT_MEASURED`. Nu recalibra repetat live neschimbat și nu prelua ratingul arbitrului.
