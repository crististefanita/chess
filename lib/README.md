# ATF Libraries

## Overview

This directory documents ATF library adoption in the Chess Lab tests.
J0 owns implementation and generic regressions; J7 verifies its own integration.

<a id="verification"></a>
<a id="verificare"></a>
## Provenance

- The [root POM](../pom.xml) installs the five JAR/POM pairs into the
  build's Maven repository. [Provenance](../library-provenance.json)
  records the identity of the delivered files.
- The [test POM](../atf-tests/pom.xml) declares Reporting and Test Kit
  directly with `test` scope. Android packages the game engine and interface.
- The [provenance probe](../atf-tests/src/test/java/ro/atf/chess/PlatformLibraryProvenanceTest.java)
  verifies the pairs. [Extent governance](../atf-tests/src/test/java/ro/atf/chess/ExtentGovernanceTest.java)
  verifies the HTML produced in J7, including diagnostics and negative controls.

## Usage guides

| Library | Use |
|---|---|
| [Core](README.Core.md) | Configuration, HTTP and JSON. |
| [Reporting](README.Reporting.md) | Extent/fallback reports, assertions and evidence. |
| [Console](README.Console.md) | Events and diagnostics in Maven and IntelliJ. |
| [Mock](README.Mock.md) | Deterministic HTTP contracts with a local catalog. |
| [Test Kit](README.TestKit.md) | JUnit lifecycle and audit. |

## Maintenance and feedback

Report Chess Lab integration issues in [J7 feedback](../docs/feedback/README.md).
For a library defect, isolate the reproduction with the J0 owner and repeat
consumer acceptance after replacing the pair.

<a id="shared-library-source"></a>

> **Library sources:** [Catalog · ATF J0](https://github.com/crististefanita/atf_j0/blob/HEAD/lib/README.md).
> This project’s POM, provenance and tests define the consumed version and local acceptance.
