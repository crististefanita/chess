# Mock — deterministic HTTP transport

## Overview

`atf-mock` replaces the Core `ApiTransport` port with a local response catalog. Use it for offline components and contracts, with controlled inputs and an explicit fallback.

## Minimal example

`mock.yaml` in the working directory:

```yaml
mock:
  routes:
    - {method: GET, path: /health, status: 200, body: '{"status":"ready"}'}
  fallback: {status: 500, body: '{}'}
```

Snippet inside a method; the transport remains offline:

```java
import atf.core.http.request.ApiRequest;
import atf.mock.AtfMockTransport;
import java.nio.file.Path;

var mock = AtfMockTransport.fromConfiguration(Path.of("mock.yaml"));
var response = mock.send(ApiRequest.of("GET", "/health"),
        "https://offline.test/health", "none").join();
```

`response.status()` is `200`; a request with no matching route receives the `500` fallback. The test independently compares the response with the expected result.

## Catalog and matching

| Need | API or contract |
|---|---|
| Explicit YAML catalog | `AtfMockTransport.fromConfiguration(Path)` |
| Project configuration | `fromProjectConfiguration()`: `atf.mock.config` > `ATF_MOCK_CONFIG` > `atf-mock.yaml/yml` |
| Request conditions | Method, path, auth profile, query, required headers, and expected body in `MockRoute` |
| Request body | Default `json` mode, negative MIME probe `wrong-content-type`; numeric matching without conversion through `double` |
| Repeated query values | `ApiQueryParameters`; includes the required values with multiplicity |

- Without configuration, `fromProjectConfiguration()` uses the `defaults()` fixture; the application catalog remains local.
- The duration in a route is response metadata, not a simulated real delay.
- A mock probe verifies integration; acceptance of the real service, broker, or device remains separate.

---

Before a new release, verify local compatibility with `PlatformLibraryProvenanceTest` and the simulated contracts used by Chess Lab. Keep coordinates, hashes and integration notes aligned with `library-provenance.json`.

<a id="shared-library-source"></a>

> **Library source:** [Mock · ATF J0](https://github.com/crististefanita/atf_j0/blob/HEAD/lib/README.Mock.md).
> This project’s POM, provenance and tests define the consumed version and local acceptance.
