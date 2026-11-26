# Chess Lab 0.17.1 · Dovezi

## Pe scurt

[Calificarea strictă](qualification.json) leagă sursa 83fd97c, APK-ul semnat, 735 de teste de componentă fără omiteri și 30 de aserțiuni Android de același ID. [Publicarea GitHub](github-release.json) identifică asset-ul. Recepțiile de download consemnează explicit revizia README citită și canalul folosit.

## Verificări

| Dovadă | Domeniu |
|---|---|
| [Componente](components.html) | Independența adversarului, analiza blocată/anulată, loturile MultiPV distincte și agregarea celor șase semimutări salvate. |
| [Analiza partidei Android](android-game-review.html) · [recepție](game-review-execution.json) | 30 de aserțiuni pe APK-ul exact, cu procese distincte pentru reluare. |
| [Stockfish Lite Android](android-stockfish-sanity.html) · [identitate](stockfish-platform.json) | 37 de aserțiuni, UCI din motorul împachetat, indicii, comparații și captură încorporată. |
| [Guvernanță EN și controale după sync](governance.html) · [RO](governance-ro.html) | 71 de verificări EN; raport separat RO. Codul executabil corespunde sursei înghețate; schimbările paralele sunt teste și documentație. Cache-ul Maven izolat păstrează execuțiile independente. |
| [Revizuirea testelor](test-change-review.html) · [dovezi executate](test-change-review.html.proof.html) · [identitate](test-change-review-execution.json) | Revizuirea sursei calificate față de baza Git explicită. |
| [Publicarea Web](web-publication.json) · [observația MCP](web-mcp-observation.json) | Octeți serviți identici, review deschis, trei taburi pe un rând și revenire la ultima mutare. Observația manuală nu este un test automat nou. |
| [Download public](public-download.json) · [autentificat](distribution-verification.json) | HTTP 200, fișier complet și CRC APK; fiecare recepție declară sursa README. |
| [Context RAG](rag.txt) · [semnătură](signing-transition.json) | Selecție consultativă, revizuirea rutelor noi și compatibilitate pentru upgrade. |
| [Livrare locală EN](local-delivery-en.html) · [RO](local-delivery-ro.html) · [identitate](local-delivery-checks.json) | 66 de verificări per limbă, inclusiv șapte controale ale arhivei cu ediția curentă. Calificarea APK-ului existent rămâne separată. |
| [RAG livrare EN](local-delivery-rag-en.txt) · [RO](local-delivery-rag-ro.txt) | Regăsirea consultativă efectivă selectează predarea locală și verificarea sa obligatorie. |
| [Modul release EN](release-module-en.html) · [RO](release-module-ro.html) · [identitate](release-module.json) | 79 de verificări per limbă; JAR independent, scripturi mutate, Git cu un singur fir purtător și semnatar neschimbat. [Dovadă revizie EN](release-module-review-en.html) · [RO](release-module-review-ro.html). Nu este calificare nouă de APK. |

Jurnalele parțiale Android au reutilizare în RAM; persistența lor după oprirea procesului rămâne în feedback. Rapoartele Extent păstrează octeții și aserțiunile originale, inclusiv capturile. Nu este declarat un câștig Elo Native.

[Retenția edițiilor](release-retention.json) păstrează 0.17.1 și 0.16.2; tagurile istorice rămân disponibile.
