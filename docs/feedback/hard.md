# Hard · priorități deschise

Selectorul public este `LEGACY`. A3 rămâne experimental: câștigul CPU inițial de 15,5% nu se confirmă pe contexte dense, unde CPU crește cu 8,85%. Nu este promovat ca optimizare generală și nu are creștere Elo demonstrată. [Rezultatele și limitele](../testing/hard-current-variants.md) · [Ordinea pașilor](../testing/hard-progress.md) · [Protocol A/B și Elo](../testing/hard-ab-elo.md)

## Ce rezolvăm acum

[Studiul celor șase copii de continuare](../testing/hard-continuation-copies.md) găsește un semnal local H8 și un candidat selectiv cu +8 prime chei / +2 trasee complete, dar niciun candidat nu se califică. Șase nepotriviri inițiale de ceas în probele arbitrului sunt corectate și păstrate. Acordul cu referința nu mai substituie calitatea WDL independentă.

| Prioritate | Pas deschis | Condiție de închidere |
|---|---|---|
| P1 | HARD-03 · regresiile continuării selective | Păstrează câștigurile candidatului selectiv, dar repară training-056/091/096 înaintea promovării: pierd WDL contra apărărilor H17/H19 libere. Componentele noi cu apeluri Native efective trec pe main și eșuează pe acel candidat. Compară prima evaluare greșită și frontiera deschisă la orizont comun; fără fallback FEN sau reîncercare doar cu buget mărit. Păstrează martorii originali de turn, damă și sacrificiu corect. Obiectivul vechi de închidere frontieră/PV rămâne deschis. [Cele șase copii](../testing/hard-continuation-copies.md) · [Mecanismul frontierei](../testing/native-forcing-frontier.md) |
| P1 | HARD-08 · referință curriculară coruptă | Kxg2 din training-060 contrazice soluția Lichess originală și pierde la ambele orizonturi ale profesorului; e4 câștigă. Corectează referința și obiectivul prin continuare atestată și compatibilă, ID/progres păstrat și teste pentru indiciu/câștig. Nu slăbi pragul de +200 material închis și nu transforma reperul perdant în răspuns etalon. Auditează alte referințe materiale înaintea învățării. [Dovezi și nepotrivirea obiectivului](../testing/hard-continuation-copies.md#regresie-reală-și-referință-curriculară-nepotrivită) |
| P1 | HARD-04 · admitere completă la învățare | Înregistratorul Lite predeclarat păstrează observații efective pe ambele tururi și review complet pentru 4/6 partide. Trei indicii indisponibile și două review-uri Advanced indisponibile rămân explicite și excluse din învățare. Capturează UCI-ul raw eșuat și rezolvă completarea mutării exacte cu proprietarul furnizorului, apoi verifică partide noi complete. Nu redenumi jurnalul Native vechi sau toate cele 300 de PGN-uri de rating drept date de învățare certificate Lite. [Lucrul furnizorului](stockfish-web.md) |
| P1 | HARD-05 · transfer și Delta Elo | Toate cele cinci porți experimentale eșuează; Delta Elo candidat rămâne nemăsurată. După repararea celor trei regresii reale și admiterea referinței, îngheață familii excluse, convertește până la rezultat și măsoară separat Delta Elo în perechi. Reperul neschimbat rămâne Hard 1.663 relativ la ancora configurată, cu anvelopă descriptivă 95% 1.531…1.763 și 13 neterminate. [Limitele reperului](../testing/current-profile-calibration.md) |
| P2 | HARD-06 · următoarea optimizare de viteză | Investighează de ce împachetarea rară pierde pe contexte dense. Profilează și testează o singură ipoteză, eventual alegerea după densitate ori alt cost real. Păstrează A3 drept martor experimental; cere controale dense și rare înaintea promovării. |
| P2 | HARD-07 · lecții care se transferă | Din primele pierderi WDL confirmate extrage familii de puzzle-uri YAML: condiții generale, apărare optimă, contraexemplu și cazuri excluse. Măsoară prima mutare, linia întreagă și conversia separat. Memorarea unui FEN nu este progres de algoritm. |

## Ownership și limite

Stockfish **Lite** este ținta pentru toate indiciile calculate, bara evaluării, deschideri, tabla manuală, explicațiile calculate și Game Review, pe Web și Android. [Găurile de integrare și testele necesare](stockfish-web.md) se gestionează acolo. APK/RAG și publicarea aparțin celuilalt AI. Basic/Advanced/Hard rămân oponenți Native.

Înghețarea sursei, reperul efectiv, bugetul YAML, cache-ul, repartizarea familiilor și regulile de audit sunt parte din [protocol](../testing/hard-ab-elo.md), nu ToDo-uri repetitive. Compară separat algoritmul, bugetul și repertoriul. Păstrează adâncimea și toate replicile cerute; timpul este o măsurătoare, nu un motiv de a slăbi jocul.

A1/A2 și aspirația nereușită sunt rezultate de studiu, nu priorități active. Experimentele de forțare respinse sunt păstrate în [studiul frontierei](../testing/native-forcing-frontier.md); corectitudinea componentei nu promovează forța de joc. Reparațiile deja protejate pentru `training-078` și continuările sale sunt în [contractul tehnic](../board/hard-candidates.md#corecția-măsurată-pentru-training-078). Nu le redeschide ca sarcini fără o regresie nouă.

Nici fereastra îngustă, nici corroborarea generală H4/H6 nu sunt promovate. Catalogul complet de 1.238 de prime decizii scade 1.214 → 1.213 în prima probă. Stockfish H17/H19 confirmă pierderea din `training-012`. `NativeHorizonGuardTest` păstrează tactica în ambele culori și perechea YAML schimb neterminat/sacrificiu corect. Rezultatele complete sunt în [dosarul tehnic](../testing/hard-current-variants.md); nu relansa aceeași strategie cu alte praguri înaintea cauzei de frontieră.

Compatibilitatea metadatelor opționale a trecut regresia originală și verificările noi: [contractul arhivei](../testing/hard-audit.md#metadate-opționale-ale-deciziei-și-recepții-vechi). Nu mai este ToDo; nu echivalează cu implementarea jurnalului Lite.

Dovezi externe: rădăcină `C:\work\sah`; experiment curent `.studii/hard-continuations/`, calibrare `.studii/native-measurement/` și frontieră `.studii/native-frontier/`. Fără partide noi ale candidatului: `Delta Elo = NOT_MEASURED`. Nu recalibra repetat live neschimbat și nu prelua ratingul arbitrului.
