# Current Native profiles: calibration and replay

[Testing strategy](README.md) · [Open Hard priorities](../feedback/hard.md) · [A/B protocol](hard-ab-elo.md)

## Purpose and boundaries

Measure Basic, Advanced and Hard independently of the coaching provider. A valid measurement can expose a weak player; successful evidence replay does not mean that every puzzle, PV or coaching request passed. This is an offline JVM measurement, with no browser, Android, APK or release qualification.

The current freeze is `3389013a9062a41fcc0ae85717bbdbd67012760e`. The public opponent is `LEGACY`. Production source, policy files and compiled classes are pinned before play. The shared H3 work is excluded from the `LEGACY_EXTRA` counter. The adaptive policy allows up to 200k extra nodes; repertoire-dependent and completed early searches may consume less. Do not describe every decision as either 44k or 200k.

## Frozen design

- Fifty distinct positions from ten authored opening families; five positions per family, eight to sixteen opening plies. These are consumed book families, **not fresh independent transfer**.
- Each profile plays both colors from every position: fifty pairs and one hundred games per profile, three hundred games overall. The starting positions and opening choices are identical across profiles.
- Opponent: the pinned desktop Stockfish 19 binary, `UCI_LimitStrength=true`, `UCI_Elo=1500`, 6,000 requested nodes, one thread and 16 MiB hash. This configured anchor does not establish a human/FIDE rating.
- A game still active at 220 total plies is unfinished. Preserve its checkpoint and report it separately from draws.
- Six games are selected **before play** for actual pinned Stockfish Lite coaching: one color pair per profile. Each actual post-prefix White/Black move attempts a 200k, MultiPV 3 Lite hint; a complete product review follows. Other rating games are not admitted to learning merely because their PGNs exist.

The anchor is desktop Stockfish 19. The coach is the pinned Lite WASM through Node CLI and the product transport. Their binaries, options and responsibilities are different. This does not qualify the Web or Android adapters. Missing advice never changes the Native choice.

## Measured results

| Profile | Resolved-game performance | Descriptive family-bootstrap 95% envelope | W / D / L / unfinished | Unfinished all-loss…all-win |
|---|---:|---:|---|---:|
| Basic | 994 | 702…1,185 | 4 / 2 / 91 / 3 | 988…1,076 |
| Advanced | 1,366 | 1,290…1,469 | 26 / 5 / 59 / 10 | 1,340…1,419 |
| Hard | 1,663 | 1,531…1,763 | 61 / 3 / 23 / 13 | 1,589…1,696 |

The point uses only resolved games: `p=(wins+draws/2)/(wins+draws+losses)`, then `1500+400*log10(p/(1-p))`. This can be biased when unfinished games differ systematically; the separate sensitivity bounds expose that risk. Do not silently assign unfinished games half a point. The descriptive interval resamples whole opening families, preserving all five color pairs, with 20,000 draws and seed 41273; it envelopes the all-loss/all-win censoring extremes. Ten family clusters and one configured anchor leave substantial uncertainty. Infinite bounds remain infinite.

This is a **new baseline**, not a measured candidate Delta Elo or proof of improvement over an older protocol. Do not merge repeated book families into the historical 600-game sample, change the app's displayed Elo automatically, or inherit Stockfish's rating. A candidate still needs its own frozen paired comparison and promotion gates.

| Profile | First accepted move / 1,238 | Complete authored route / 1,238 | Actual Native decisions |
|---|---:|---:|---:|
| Basic | 853 | 773 | 1,622 |
| Advanced | 1,114 | 1,067 | 1,977 |
| Hard | 1,214 | 1,188 | 2,198 |

For Hard, all 1,000 mate exercises complete on the authored routes. Material exercises complete **188/238**: tactics 87/100, training 79/114 and intermezzo 22/24. Fifty exercises remain incomplete. The benchmark calls the ordinary opponent before reading the quest verdict; it does not supply a mate objective, solution or next defense to the search. Full-route success here means the catalog's authored defense, **not every free alternative defense**. Separate all-defense proofs retain their original scope in [Hard status](hard-status.md).

## Collector failures and technical continuation

The original collector stopped five games: three at overly restrictive Native receipt checks and two during Lite advice. Their actual prefixes and failures remain immutable. Five continuations reuse those exact original moves and observations, then start a new anchor session with the same settings and full history. The weaker Stockfish player is nondeterministic; these technical session resets are disclosed, not called bit-identical continuations of its internal hash/random state. Restart-from-start retries were retained separately and excluded from the cohort.

Two Native decisions legitimately depart from the raw top score under the existing history/repetition policy. `AiProfileEvidence` independently replays that selection contract. It still rejects an arbitrary legal substituted move, changed work, empty ranking, foreign run, changed file or invented draw. This closes a **collector assumption**, not a search improvement.

The third Native receipt contains a legal seven-ply capture continuation under an ordinary H5 label, without a separate typed closure certificate. A game result can be replayed legally without declaring that continuation a complete adversarial proof. That quality goal stays **FAIL**. The stricter puzzle/proof checker is retained, not weakened to make this case green.

Four of the six predeclared Lite games have all pre-move hints and a complete final review. The two Advanced games retain three unavailable hint attempts and two unavailable reviews, with `No exact PV for Stockfish bestmove`. They are **not admitted to learning**. The failing transport calls do not expose their full raw transcript in the failure object; capturing that evidence before a provider fix is an open task in [Lite feedback](../feedback/stockfish-web.md). Complete coaching is an admission condition for further study, not independent H17/H19 certification of every offered continuation.

## Audit and repeat procedure

1. Freeze the current source, configuration, classpath, starts, partitions, provider identities and selection **before** measurement. Keep any changed collector separately identified; never rebuild playing classes during a run.
2. Retain each original Native observation and anchor UCI before committing its move. Keep the PGN, checkpoint, run ID, outcome and bound raw ledger. For selected learning games, retain actual Lite attempts on both turns and the complete review or typed failure.
3. Replay every PGN with the independently pinned chess.js 1.4.0; compare legal UCI history and terminal result. Replay Native accounting, history-aware selection and puzzle verdicts through the frozen JVM contracts. Bind Lite cohorts to raw UCI, full context, provider and common depth.
4. Reject incomplete color pairs, duplicate games and unequal family samples before fitting. Preserve capped games and technical continuations explicitly. `AiProfileRatingTest` covers known results, zero resolved games, infinities, duplicates, missing colors and family imbalance.
5. Generate separate original ATF Extent results for **measurement integrity**, component contracts and **quality goals**. A PASS on imported evidence cannot overwrite a Native proof failure, incomplete review or unsolved catalog goal.
6. Update the owning guide and feedback only after original outcomes and report contents are checked. A diagnostic calibration does not trigger APK publication or update a qualified in-app rating.

Constant evidence root: `C:\work\sah`; current relative collection `.studii/native-measurement/`. Its README indexes the frozen plan, original failures, exact-prefix continuations, independent PGN replay, Native/puzzle ledgers, ratings, original component Extent and the three explicit quality-goal failures. Build/output artifacts stay outside this repository. The small pinned counterexample fixture is test-only, not a runtime move book.
