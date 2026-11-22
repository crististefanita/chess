# Chess Lab 0.16.2 · Dovezi

## Pe scurt

[Calificarea strictă](qualification.json) leagă commitul sursă `a109db7`, APK-ul semnat, 608 aserțiuni de componentă și 30 de aserțiuni native Game Review de același ID de rulare. [Publicarea GitHub](github-release.json) identifică APK-ul încărcat. Ruta din README-ul publicat a trecut verificarea descărcării complete [publice](public-download.json) și [autentificate](distribution-verification.json), inclusiv CRC-ul APK-ului.

| Dovadă | Ce demonstrează |
|---|---|
| [Componente](components.html) | Aserțiuni expandabile din suita de 608 teste, fără cazuri omise. |
| [Game Review Android](android-game-review.html) · [execuție](game-review-execution.json) | APK semnat, același ID, captură nativă și 30 de aserțiuni în fazele inițială, De ce și restaurată. |
| [Analiza partidei Android](android-match-analysis.html) · [execuție inițială](match-analysis-execution.json) · [execuție restaurată](match-analysis-restore-execution.json) | Proba țintită pe APK-ul semnat pentru eticheta din istoric și indiciul după restart; dovadă suplimentară în afara calificării stricte. |
| [Partide salvate](android-match-slots.html) · [execuție](match-slots-execution.json) · [Partide încheiate](android-match-archive.html) · [execuție](match-archive-execution.json) | Câte două aserțiuni pe APK-ul semnat pentru fiecare meniu, ID nativ comun, amprentele capturilor și identitatea fazelor pereche; dovezi suplimentare. |
| [Focusul analizei Web](web-review-focus.html) · [recepția browserului](web-review-focus.json) | Verificarea automată în browserul local a alinierii titlului la text 130%; proba manuală MCP pe site-ul servit rămâne separată. |
| [Publicarea Web](web-publication.json) | Deployul Pages nou a reușit; indexul și CSS-ul Game Review servite corespund octet cu octet obiectelor Git calificate. |
| [Guvernanță](governance.html) · [revizuirea mutării testelor](test-change-review.html) · [dovada revizuirii](test-change-review.html.proof.html) | Controalele de release, documentație și mutare a testelor. |
| [Guvernanța publicării](publication-governance.html) | Reverifică README-ul publicat, arhiva calificată și recepțiile descărcărilor publică și autentificată. |
| [Context RAG](rag.txt) · [continuitatea semnării](signing-transition.json) | Ghidurile afectate și continuitatea certificatului. |

Fișierele Extent HTML își păstrează octeții calificați. Tagul sursei și certificatul urmărit în proiect identifică aceeași cheie de semnare; certificatul nu a fost rotit pentru această ediție.
