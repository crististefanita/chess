# Chess Lab Releases

## Overview

The catalog identifies the available release and where to verify its evidence.

## Published Release

[`index.json`](index.json) identifies the current release and retains the immediately preceding release as offline historical comparison evidence. GitHub retains only the current release and version tag; the comparison receipts do not promise a second available download. The main README points only to the current release.

| Role | Version | Evidence |
|---|---|---|
| Current | 0.17.1 | [APK notes and receipts](0.17.1/README.md) |
| Previous | 0.16.2 | [Comparison release](0.16.2/README.md) |

## Evidence and Archiving

- The GitHub Release distributes the APK. The release directory holds the
  notes, Extent reports, receipts, and RAG context.
- Find retired source identities in their original receipts and Git history; obsolete release and deployment tags are removed.
- After promotion, verify the download from the `main` README and save
  the evidence alongside the release.

## Candidate

Qualify the candidate and publish the asset before updating
`currentVersion` and the README links. Verification from the published README
closes the process. [Order of operations](../docs/release/README.md#qualification-and-publication)

[Release process](../docs/release/README.md) · [Published release evidence](0.17.1/reports/README.md) · [Previous comparison](0.16.2/reports/README.md)
