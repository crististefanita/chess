# Bibliotecile ATF

## Pe scurt

Acest director documentează adoptarea bibliotecilor ATF în testele Chess Lab.
Implementarea și regresiile generice aparțin J0; J7 verifică integrarea proprie.

<a id="verification"></a>
<a id="verificare"></a>
## Proveniență

- [POM-ul rădăcină](../pom.xml) instalează cele cinci perechi JAR/POM în
  depozitul Maven de build. [Proveniența](../library-provenance.json)
  fixează identitatea fișierelor livrate.
- [POM-ul testelor](../atf-tests/pom.xml) declară Reporting și Test Kit
  direct, cu scope `test`. Android împachetează motorul și interfața jocului.
- [Proba de proveniență](../atf-tests/src/test/java/ro/atf/chess/PlatformLibraryProvenanceTest.java)
  verifică perechile. [Guvernanța Extent](../atf-tests/src/test/java/ro/atf/chess/ExtentGovernanceTest.java)
  verifică HTML-ul produs în J7, inclusiv diagnosticul și probele negative.

## Ghiduri de utilizare

| Bibliotecă | Utilizare |
|---|---|
| [Core](README.Core.md) | Configurație, HTTP și JSON. |
| [Reporting](README.Reporting.md) | Rapoarte Extent/fallback, aserțiuni și dovezi. |
| [Console](README.Console.md) | Evenimente și diagnostic în Maven și IntelliJ. |
| [Mock](README.Mock.md) | Contracte HTTP deterministe, cu un catalog local. |
| [Test Kit](README.TestKit.md) | Lifecycle și audit JUnit. |

## Mentenanță și feedback

Raportați integrarea Chess Lab în [feedback-ul J7](../docs/feedback/README.md).
Pentru un defect al bibliotecii, izolați proba la ownerul J0 și repetați
acceptanța consumatorului după înlocuirea perechii.

<a id="shared-library-source"></a>

> **Sursa bibliotecilor:** [Catalog · ATF J0](https://github.com/crististefanita/atf_j0/blob/HEAD/lib/README.md).
> POM-ul, proveniența și testele acestui proiect stabilesc versiunea consumată și acceptanța locală.
