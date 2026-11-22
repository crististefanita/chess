# Test Kit — JUnit classification and audit

## Overview

Kit provides annotations, evaluated comparisons, and a neutral lifecycle. The JAR does not start a browser, broker, or ADB, and does not publish Extent on its own.

| Need | API and limit |
|---|---|
| Test level | `@AtfUnitTest`, `@AtfComponentTest`, `@AtfIntegrationTest`, `@AtfE2eTest`; classification only, with no automatic launch. |
| Auditable comparisons | `RecordedAssertions`; the JUnit verdict precedes the 240-character preview, which preserves complete Unicode pairs. |
| Existing JUnit assertions | `AuditedAssertions` within the adapter scope; lazy diagnostics, original identity and cause. Direct JUnit calls do not become auditable automatically. |
| Full values | `AssertionValueDisplay` can receive a `Projection` observer with the expected/actual role and redacted text before preview; the consumer owns retention. |
| Phases and cleanup | `ComponentAuditExtension` / `ComponentAuditAdapter`, `TestRunInitialization`, `FailureAwareCleanup`. |
| Policies and resources | `Atf*Policy` resolvers; `readResourceUtf8` for classpath/JAR, `requiredPath` for a real file. |

## Minimal example

The adapter owns the recorder and publishes the result; a mismatch preserves the original JUnit exception:

```java
import atf.testkit.assertions.RecordedAssertions;

public static void verifyState(RecordedAssertions audit, String actual) {
    audit.equals("State", "ready", actual);
}
```

## Scope and integration

- J7 declares `atf.api:atf-test-kit` with `test` scope and an explicit version in the [test POM](../atf-tests/pom.xml). Where a project provides test support in `main`, `compile` scope belongs to that support module. The J7 APK contains game code.
- JUnit Jupiter API and Jackson are transitive. The runner declares the `junit-jupiter-engine` and its selection; the level annotation does not replace `@Test` or the consumer extension.
- For Extent: the adapter sends checks to `AssertionAuditReport`; `AssertionAuditRun` owns the shared session. Kit does not copy the renderer. [Reporting integration](README.Reporting.md#scope-and-integration).
- Inject the sanitizer through `AssertionValueDisplay` before preview. The comparison runs once; stored values are bounded and are not a complete archive. Pure units keep their own policy.
- Scopes and resources are per invocation; close them and preserve the original cause. A local lock does not isolate global policies from external readers.


---

Verify local contracts with `PlatformLibraryProvenanceTest` and `SuiteGovernanceTest`. Keep coordinates, hashes and integration notes aligned with `library-provenance.json`.

<a id="shared-library-source"></a>

> **Library source:** [Test Kit · ATF J0](https://github.com/crististefanita/atf_j0/blob/HEAD/lib/README.TestKit.md).
> This project’s POM, provenance and tests define the consumed version and local acceptance.
