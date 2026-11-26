# Native Hard: studiul frontierei în șah

[Priorități curente](../feedback/hard.md) · [Protocol A/B și Elo](hard-ab-elo.md) · [Proces de îmbunătățire](hard-progress.md)

## Domeniu

Oponentul public rămâne `LEGACY`. Reperul experimental este `c396c32`; integrarea testelor și documentației folosește separat main curent. Stockfish Lite deține indiciile produsului, bara și review-ul. Aici nu construim APK.

Candidatul închide frunza în șah prin toate evaziunile legale, recursiv cât timp șahul continuă. Vizitele consumă aceeași rezervă adaptivă de 100k–200k noduri suplimentare; epuizarea bugetului sau limitei de siguranță întrerupe iterația. Închiderea acoperă șahul, nu întreaga liniștire cu capturi. Componentele verifică cache gol/încălzit/dezactivat, ferestre înguste/complete, întreruperea și un sacrificiu corect de damă.

[Implementarea Stockfish](https://github.com/official-stockfish/Stockfish/blob/master/src/search.cpp) separă tratarea șahului de evaluarea statică. Susține corecția locală, nu o afirmație de forță. [Ghidul de dezvoltare Stockfish](https://official-stockfish.github.io/docs/stockfish-wiki/Developers.html) cere și testarea schimbărilor funcționale de căutare.

## Rezultate și respingere

| Verificare | Rezultat efectiv | Semnificație |
|---|---|---|
| Frunza în șah salvată din training-012 | Static +8 cp; minimax cu evaziuni libere +901 cp pentru partea care recapturează | Defect local reprodus. Sunt scoruri interne Native, nu scala Stockfish. |
| Prototip focalizat | 58/58 verificări | Corectitudinea componentei nu califică forța de joc. |
| Catalog complet de prime alegeri | Părinte 1.214/1.238; candidat diagnostic 1.208/1.238 | Șapte chei recuperate, douăsprezece pierdute și o recepție respinsă. Liniile integrale nu sunt certificate. |
| Basic/Advanced | 48/48 perechi exacte | Alegerile, PV-urile ordonate, adâncimea și lucrul suplimentar rămân identice. |
| training-021 | Qxf5+/Qxf4+ pierde 500/498‰ WDL la Stockfish H17/H19 | Ambii martori echivalenți ca orientare resping promovarea. |
| Trei tactici | tactic-020/031/052 pierde 500/1.000/901‰ la H19 | Pierderi confirmate, nu simple diferențe față de catalog. |
| Normalizare H3 taxată | 35/35 după corecția API26; pierde în continuare training-021 la H6 închis, sub 40k noduri suplimentare | Amestecul seminței H3 vechi cu noua frontieră nu explică singur eșecul. Nu s-a rulat catalogul complet sau un duel pentru acest candidat respins. |

Catalogul strict se oprește la `training-097`: continuarea veche de verificare a capturilor nu poate fi etichetată doar ca evaziune din șah. O execuție diagnostică separată a păstrat toate cele 1.238 de observații, cu acea recepție explicit respinsă și exclusă din rezultate acceptate. Inferența inițială `training-055` era greșită: ordinea catalogului nu este numerică.

Guvernanța existentă a detectat și un accessor Java List prea nou în primul prototip normalizat. Sursa corectată folosește accesul indexat compatibil API26. Rulările eșuate și corectate sunt păstrate separat.

## Rădăcină corectă și explicație greșită

În training-021 părintele alege Qg7+ câștigător, dar PV-ul salvat este `Qg7+ Ke8 Qg8+ Kd7 Qxd8+`. Negrul poate juca legal Kxd8. Linia cu culorile inversate are același defect. Stockfish confirmă valoarea primei mutări, nu această continuare Native.

`NativeFrontierRegressionTest` separă cinci martori cu prima mutare câștigătoare de două contraexemple ale PV-ului salvat. Dovezile profesorului păstrează 42 de faze H17/H19 exacte pe șapte contexte. Cele două orientări training-065 rămân obiective de studiu: candidatul recuperează doar una. Recuperarea parțială nu califică promovarea Native.

Fixture-urile YAML sunt exclusiv de test: origini/istoric explicite, UCI original, identitatea binarului și amprentă de integritate. Validarea offline rejoacă PV-uri legale, verifică scoruri/WDL/adâncime din raw și respinge tabla, orizontul ori mutarea optimă schimbate. Lecția PV-ului greșit nu cere motorului viitor să păstreze linia proastă. Niciun fixture nu suprascrie mutări în runtime.

## Următoarea intervenție delimitată

[Calibrarea curentă](current-profile-calibration.md) păstrează un martor de plan liniștit: după `Qg7+ Ke6`, Qd6 leagă Rg3 de Kh2. Linia Stockfish H19 capturată folosește **Kh3**, apoi răspunde la Qb8 cu **Re3+**. Contraexemplul sintetic cu nebun pe f5 face Kh3 ilegal. Este o lecție generală de legalitate și plan, nu o regulă necondiționată pentru rege sau o suprascriere FEN.

Probele izolate pe rădăcini alese ale candidatului încă preferă Qxf5+ față de Qg7+: la H6, +90 față de −20 cp Native; la H8, +89 față de −15. Ambele probe H10 epuizează propriile două milioane de noduri fără scor exact. Sunt probe independente pe rădăcină ale candidatului respins, nu bugete comune din jocul public. Adâncimea până la H8 nu repară ordinea; H10 incomplet nu este succes. Verifică apărările liniștite libere și planul dezlegării înainte de altă creștere de buget.

Compară Qg7+ cu Qxf5+ prin apărări libere, la același orizont normal și forțat, păstrând Kxd8 în linia turnului capturat. Verifică de ce Native preferă Qf6 față de Ke7 după Qxf5+: ferestrele H6 izolate le dau +90/+92. Ramura Ke7 rămâne +91 la H8; cealaltă ramură H8 nu termină în propria probă de 200k. Aceste probe nu constituie un buget comun de partidă și nu demonstrează remiza.

Deciziile training-021 respinse termină H6 cu buget disponibil: epuizarea nodurilor nu explică singură eșecul. Păstrează calitatea rădăcinii și a continuării drept porți separate. Orice recepție viitoare numește modul de forțare și lucrul taxat; nu lărgi certificatul de evaziune pentru a accepta semimutări liniștite arbitrare. Transferul pe familii excluse și duelurile urmează porțile relevante.

## Dovezi

Rădăcină constantă: `C:\work\sah`; studiu relativ `.studii/native-frontier/`. Indexul leagă Extent original, manifestele, registrele Native și fazele profesorului. ID-urile de asociere ale profesorului sunt posthoc, nu ID-urile originale ale execuției Native. Contextele consumate și reflexiile nu sunt familii independente noi.

Nu s-a jucat niciun duel încheiat: **Delta Elo = NOT_MEASURED**. Candidații respinși rămân externi; fixture-urile calificate de regresie și documentația se pot integra independent.
