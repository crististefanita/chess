# Chess Lab 0.17.1 · Evidence

## Overview

[Strict qualification](qualification.json) binds source 83fd97c, the signed APK, 735 component tests without skips and 30 Android assertions to one run ID. [GitHub publication](github-release.json) identifies the asset. Download receipts explicitly identify the README revision and access channel used.

## Checks

| Evidence | Scope |
|---|---|
| [Components](components.html) | Independent opponent progress, stalled/cancelled analysis, distinct MultiPV cohorts and aggregation of six saved plies. |
| [Android Game Review](android-game-review.html) · [receipt](game-review-execution.json) | 30 assertions on the exact APK, with separate processes for resumption. |
| [Android Stockfish Lite](android-stockfish-sanity.html) · [identity](stockfish-platform.json) | 37 assertions, actual packaged-engine UCI, hints, comparisons and an embedded capture. |
| [EN governance and post-sync controls](governance.html) · [RO](governance-ro.html) | 71 EN checks; separate RO report. Executable code matches the frozen source; parallel changes are tests and documentation. An isolated Maven cache keeps executions independent. |
| [Test-change review](test-change-review.html) · [executed proof](test-change-review.html.proof.html) · [identity](test-change-review-execution.json) | Review of the qualified source against an explicit Git baseline. |
| [Web publication](web-publication.json) · [MCP observation](web-mcp-observation.json) | Matching served bytes, an opened review, three tabs in one row and restoration of the last move. Manual observation is not another automated test. |
| [Public download](public-download.json) · [authenticated](distribution-verification.json) | HTTP 200, complete file and APK CRC; each receipt declares its README source. |
| [RAG context](rag.txt) · [signing](signing-transition.json) | Advisory selection, review of new routes and upgrade compatibility. |
| [Local delivery EN](local-delivery-en.html) · [RO](local-delivery-ro.html) · [identity](local-delivery-checks.json) | 66 checks per language, including seven latest-only archive controls. Qualification of the existing APK remains separate. |
| [Delivery RAG EN](local-delivery-rag-en.txt) · [RO](local-delivery-rag-ro.txt) | Actual advisory retrieval selects the local handoff and its mandatory verification. |

Partial Android work currently reuses RAM; persistence after process death remains in feedback. Extent preserves original bytes and assertions, including captures. No Native Elo gain is claimed.

[Release retention](release-retention.json) keeps 0.17.1 and 0.16.2; historical tags remain available.
