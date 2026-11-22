# Chess Lab 0.16.2 · Evidence

## Overview

The [strict qualification](qualification.json) binds source commit `a109db7`, the signed APK, 608 component assertions and 30 native Game Review assertions to one run ID. [GitHub publication](github-release.json) identifies the uploaded APK. Download receipts are added after the published README points to this edition.

| Evidence | What it proves |
|---|---|
| [Components](components.html) | Expandable assertions from the 608-test suite, without skipped cases. |
| [Android Game Review](android-game-review.html) · [execution](game-review-execution.json) | Signed APK, same run ID, native capture and 30 assertions across initial, Why and restored phases. |
| [Android match analysis](android-match-analysis.html) · [initial execution](match-analysis-execution.json) · [restored execution](match-analysis-restore-execution.json) | Focused signed-APK check of the selected history label and hint after restart; supplementary evidence outside strict qualification. |
| [Web review focus](web-review-focus.html) · [browser receipt](web-review-focus.json) | Automated local browser check of compact 130% title alignment; a served-site manual MCP check remains separate. |
| [Governance](governance.html) · [test-change review](test-change-review.html) · [review proof](test-change-review.html.proof.html) | Release, documentation and test-movement checks. |
| [RAG context](rag.txt) · [signing transition](signing-transition.json) | Affected guidance and certificate continuity. |

The Extent HTML files preserve their qualified bytes. The source tag and the repository-tracked certificate identify the same signing identity; no certificate rotation was made for this release.
