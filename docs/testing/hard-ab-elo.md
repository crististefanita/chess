# Native Hard: îmbunătățire controlată și Elo

## Scop și responsabilități

Îmbunătățim adversarul Java prin dovezi reproductibile. Stockfish Lite deține indiciile produsului, bara de evaluare și review-urile; este o observație de profesor, nu generatorul mutărilor Native Hard. Executabilul Windows Stockfish poate fi arbitru offline separat. Nu atribuim adversarului Java ratingul acestuia sau o PV mai lungă. [Contractul analizei](../feedback/stockfish-web.md) · [Prioritățile curente](../feedback/hard.md)

Protocolul este independent de versiune. Experimentele se păstrează în `C:\work\sah`, cu căi de dovezi relative la acea rădăcină. APK-ul, RAG și publicarea aparțin responsabilului de livrare; documentația și comparațiile offline nu califică un APK.

## Înghețarea reperului real

Înregistrați commitul imuabil, diff-ul local, hashurile sursei/buildului, versiunea Java, selectorul public efectiv, digesturile YAML/repertoriului, definiția contabilizării nodurilor și partiția pozițiilor înainte de evaluare. Citiți adversarul configurat, fără a presupune că o clasă Master sau Next este live. Schimbările deschiderilor cer reper nou; un Elo anterior nu certifică alt repertoriu.

Apelați `analyzeBot` o dată pentru fiecare decizie Native și păstrați raportul acelei decizii. O căutare suplimentară doar pentru atașarea unei PV mai lungi schimbă observația. Mutarea jucată trebuie să coincidă cu alegerea Native auditată; indiciul Stockfish însoțitor nu poate deveni implicit adversarul.

| Braț | Modificare | Acceptare principală |
|---|---|---|
| Reper | Adversarul Native public curent înghețat | Sursă, politică, mutări și contabilizare reproductibile. |
| A: viteză | O optimizare a reprezentării/alocării/operațiilor pe biți | Ordine legală, perft, decizii, adâncimi și contoare identice la același lucru. Măsurați alocări, CPU proces/fir și timp real în JVM-uri noi seriale. |
| B: forță | Un mecanism general de căutare sau evaluare | Minimax/limite corecte, contraexemple critice, transfer pe familii independente și conversie completă, apoi duel propriu. |
| Studiu de buget | Politici fixe și adaptive | Raport separat de algoritm. Înghețați activarea adaptivă înainte de holdout; taxați confirmarea, lucrul forțat și reluările. |

Un test de forță nu poate include implicit A, repertoriu nou, buget nou și evaluator nou simultan. După calificarea individuală, combinația devine alt braț numit explicit.

## Componente înaintea campaniilor mari

- Comparați scoruri exacte și tipuri de limite cu minimax exhaustiv pe poziții legale mici. Acoperiți EXACT/LOWER/UPPER, iterații întrerupte, cache rece/cald/dezactivat, repetiții, contoare, en passant, rocadă, distanță de mat și promovare.
- O frunză în șah nu poate rămâne nemișcată. O continuare incompletă de capturi/șahuri nu este scor final exact. Păstrați sacrificiile valide și contraexemplele capturilor greșite; fără suprascrieri FEN-soluție.
- Pentru viteză, comparați cu un oracol independent al legalității și perft. Includeți legări, atacuri descoperite, capturi de rege, en passant și ordinea promovărilor. Egalitatea nodurilor nu dovedește singură aceeași semantică.
- Pentru reutilizarea N→N+1 păstrați istoricul, culoarea, drepturile, contoarele, politica și adâncimea rămasă. Revalidați limitele cache-ului în fereastra curentă. O lecție sau un rezultat memorat nu dovedește generalizarea.
- Păstrați Basic și Advanced identice fără autorizarea explicită a acelui domeniu. Refactorizarea candidatului experimental nu îl poate selecta implicit ca adversar public.

Folosiți cel puțin patru JVM-uri noi încălzite la comparația inițială de viteză, alternând ordinea brațelor. Raportați fiecare proces și lucrul executat. Economiile de alocare cu rezultate CPU/timp inconsecvente rămân necalificate. La noduri egale, refactorizarea exactă păstrează forța; cheltuirea timpului economisit cere experiment separat la timp egal.

## Dovezi din probleme și partide

Partiționați după partida sursă și familia poziției, ținând transpozițiile și echivalentele cu culori inversate împreună. Înghețați studiul, validarea și evaluarea finală înainte de scorarea profesorului sau reglaj. O ratare consultată devine regresie, nu alt holdout nou.

Raportați separat cheia, linia completă, apărările libere și conversia finală. Folosiți Syzygy unde se aplică, păstrând culoarea, drepturile, contoarele și regulile remizei. Lecțiile YAML cer precondiții generale, apărarea bună, contraexemplu și transfer pe familii excluse. Soluțiile exacte memorate pot servi catalogul educațional, fără a fi progres al căutării.

Pentru fiecare semimutare Albă/Neagră dintr-o partidă auditată păstrați:

1. Identitatea rulării/partidei/semimutării, prefix complet, FEN, actor și decizia/raportul Native efectiv cu sursă, politică, adâncime și lucru măsurat.
2. Indiciul Stockfish pentru același context anterior mutării, UCI original, identitatea furnizorului/buildului/rețelei, opțiuni, noduri efective, adâncime încheiată, scor, WDL și PV legale.
3. Comparația liber/jucat a aceluiași arbitru la o adâncime comună încheiată. Păstrați CP/mat brut când WDL saturează. Nu comparați direct centipionii Native cu cei Stockfish.
4. Mutarea executată și review-ul final complet legat de același istoric. Lucrul lipsă rămâne lipsă; reconstrucția ulterioară nu transformă indiciile în observații originale.

Pentru același arbitru și aceeași perspectivă, pierderea WDL este `max(0, E(best) - E(played))`, unde `E = W + D/2`, în miimi. Este scor așteptat, nu probabilitatea de câștig a unui om. Lungimea PV, adâncimea încheiată și cea selectivă sunt diferite. [Definițiile UCI Stockfish](https://official-stockfish.github.io/docs/stockfish-wiki/UCI-Protocol-and-Stockfish-Commands.html)

Grupați primele pierderi importante după mecanism și pierdere WDL cumulată verificată. Corectați familia reproductibilă cu impact maxim, fără rotație infinită între probleme numite. După trei mecanisme eșuate, revizuiți ipoteza și dovezile înaintea altei probe apropiate; nu relaxați controalele.

## Corectitudine, diagnostic și promovare distincte

Mutările ilegale, remiza/matul greșite, limitele corupte sau dovezile false blochează interpretarea duelului. Un candidat legal care pierde un control tactic poate intra într-un duel explicit **diagnostic, nepromovabil** pentru măsurarea compromisului. Acesta nu repară poarta, nu îi relaxează pragul și nu furnizează rating calificat de release.

Promovarea cere controalele critice, regresia etapelor acceptate, transfer independent și conversie completă. Apoi comparați direct candidatul cu reperul înghețat; recalibrarea codului live neschimbat contra unei ancore nu măsoară progresul candidatului.

## Partide pereche și Elo

- Înghețați 100 de starturi/prefixe autentic noi pentru comparația inițială: două partide cu culori inversate per start, 200 total. Păstrați PGN complete, opțiuni, ieșiri și rezultatul fiecărei partide.
- Estimați incertitudinea având perechea inițială ca unitate independentă sau folosiți un model pentanomial/SPRT documentat. Mutările și reflexiile problemelor nu sunt eșantioane independente de duel.
- Declarați înainte lotul de confirmare și oprirea. Dacă intervalul este prea larg, extindeți cu perechi noi conform acelei reguli, fără a repeta până apare un interval pozitiv. Confirmarea poate avea 200–1.000 de perechi, potrivit efectului și resurselor.
- Raportați victorii/remize/înfrângeri și neterminate separat. Finalizați partidele plafonate când se poate; altfel arătați sensibilitatea la tratarea tuturor drept victorii sau înfrângeri, fără remize implicite într-un rating promovat.
- Pentru partidele încheiate, `p = (W + D/2)/(W + D + L)` și `Delta Elo = 400 log10(p/(1-p))`. Scorurile de margine au estimări nemărginite. Intervalele pe perechi și politica jocurilor cenzurate însoțesc estimarea.
- Stockfish cu `UCI_Elo` 1500/1600/1700 este ancoră externă specifică protocolului. Consemnați executabil, rețea, opțiuni, timp/noduri și hardware. Nu este rating uman certificat sub controlul nostru diferit. Raportați estimări relative și intervale; nu combinați campanii incompatibile.

Păstrați noduri egale, buget adaptiv și timp egal în rânduri diferite. `LEGACY_EXTRA` Native omite lucrul comun și nu este totalul nodurilor sau NPS total. Nici nodurile cerute egale între motoare nu înseamnă calcul egal per nod. [Metodele statistice Fishtest](https://official-stockfish.github.io/docs/fishtest-wiki/Fishtest-Mathematics.html)

## Rezultat auditabil și decizie

Folosiți ATF Reporting/Extent pentru suitele de componente și platformă. Păstrați așteptat/observat/verdict original, sursă/rulare, rezultate brute ale motorului și capturi necesare afirmației. Respingeți aserțiuni lipsă, PNG schimbat, istoric diferit și rulări străine. Un mock dovedește contractul său, nu execuția Android.

Guvernați ratingul prin componente: bilanțuri cunoscute și margini; reproductibilitatea intervalului/seedului; starturi oglindite într-un cluster; partide lipsă sau duplicate; PGN/rezultat modificat; motor/sursă/opțiuni străine; politica neterminatelor și estimare fără interval. Zero partide complete produce `NOT_MEASURED`, nu zero Delta Elo sau un rating vechi redenumit nou.

Studiul minim util are index, Extent autonom, PGN complete și jurnalul pe mutare/final necesar redării. Păstrați alte loguri doar pentru eșecuri restante sau verificare independentă, fără capturi și rezumate duplicate în proiect.

Fiecare iterație se încheie cu ID-uri reper/candidat, mecanism, teste/regresii critice, transfer nou, conversie/apărare, bilanț/neterminate, Delta Elo candidat–reper cu IC 95% sau `NOT_MEASURED`, costuri efective și verdict promovare/respingere/neconcludent. O alegere neschimbată într-un lot mic nu măsoară Delta Elo zero sau progresul.