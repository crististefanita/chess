# Decision-local Native search reuse

[Stockfish lessons](hard-stockfish-lessons.md) · [Open priorities](../feedback/hard.md) · [Measurement protocol](hard-ab-elo.md)

## Scope and cache contract

This external child extends the typed-bound candidate with one **22,000-entry shared LRU** across a Hard decision's ordinary search and selected confirmation phases. The comparison uses identical compiled code with `hard-search-reuse.yaml` enabled or disabled; budgets, evaluator and opening repertoire stay fixed. Basic and Advanced do not use the table.

The table lives for one complete decision. Its owner retains the root, full prior history, quiet clock, repetition offers and opening context. No process-global score bank, cross-game lookup or reuse after the next played move is implemented. Those require separate history and mate-distance normalization contracts.

Entries retain EXACT, LOWER or UPPER information. A bound may answer only a sufficient requested window; it never becomes an exact score or a displayed replacement PV. Keys preserve position/rights/en passant, remaining depth, mate distance, objective, extension flags and the ordinary versus forcing frontier. Incomplete frames publish no result; completed child frames may remain usable. Every visited cache hit is still charged as a visit, and all confirmation work remains within the inherited declared extra-node ceiling.

The source references are Stockfish 19's [transposition contract](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/tt.h) and [search implementation](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/search.cpp). Their ideas motivate the experiment; their pruning constants and deeper-horizon values are not copied as certified J7 answers.

## Measured first prototype

The full **1,238-route frozen curriculum**, before the separate training-060 correction, retains **1,222 first keys / 1,193 routes** and every previously accepted key/route. It preserves 056/091/096 and all eight original first-key recoveries. On **2,219 matched contexts**, extra work falls **150,499,363 to 136,987,390 (8.98%)**. The 53-context consumed focus saves 13.89%; it is a different sample. The disabled-cache control reproduces all 53 complete parent observations, and Basic/Advanced remain identical on 26 contexts / 52 actual calls.

A separately frozen batch of 24 public Lichess source games excludes the named prior source batch and catalog boards. All 24 roots remain unchanged while extra work falls **4,362,349 to 3,397,024 (22.13%)**. The **100 exact Stockfish H17/H19 phases** include free, actual and authored roots. Mean H19 WDL loss is 62.69 per-mille for both arms. This is source-held-out first-choice evidence, not full conversion or an Elo measurement.

Four fresh serial JVMs in ABBA order measured those 24 now-consumed contexts twice per arm, after three unmeasured warmups each. Total thread CPU fell **18.44%** and total process CPU **11.91%**. These are observations on one VM with only two JVM repetitions per arm, not a general speed or Android qualification. Node savings, CPU and playing strength remain separate outcomes.

Three previously unaccepted catalog contexts change root. Additional **18 exact teacher phases** retain the win in tactic-059; training-081 still loses with either choice. In training-090, H17/H19 WDL is worse by **86.5 / 109.5 per-mille**. Both choices miss the winning `...Rxh3+`. This remains a strict nondegradation **FAIL**, even though it is below the predeclared 150-per-mille large-regression admission threshold. The diagnostic match cannot silently turn that defect into a pass or promote the engine.

## Corrected proof-memory boundary

Pre-match review found that the separate promotion proof still subtracted the old local table's size. Shared-table phases leave that local map empty, so a populated cache could receive a second full proof allowance. A real component reproduces a completed certificate granting **22,000 instead of 21,980** entries; the original report preserves seven passes and that one failure.

The separate v2 child subtracts actual shared occupancy; disabling reuse retains the original local-table behavior. **22 actual component methods pass**, including the real certificate, interruptions, exact/bound windows, independent small-tree minimax and the protected continuations. A further **53 actual focused Native calls** reproduce the first prototype's complete observations.

The source delta is confined to the existing `PawnPushProof.eligible` branch. An inventory of all 2,219 frozen catalog contexts and the 24 source roots finds no eligible case. Their earlier Native/teacher observations are therefore explicitly inherited through that guarded differential; they are **not relabelled as 1,238 new v2 runs**. The new eligible branch is exercised by the certificate component. The CPU observations above remain the original prototype's measurements.

The initial paired launch was stopped before using its outcome distribution for tuning. Two completed receipts and four unfinished prefixes, 213 independently replayed frames with zero defects, remain an aborted preflight. They produce no Elo and cannot become v2 outcomes. The corrected v2 run was later stopped at the user's requested **100-game audited checkpoint**. Its independent replay covers 11,139 accepted frames with zero defects: 94 terminal results and six games capped unfinished. The prefix contains 49 full color-reversed start pairs and two singleton starts, so it is not the planned 100-pair sample and **Delta Elo is not measured**. Five later receipts remain preserved but are excluded from this cutoff. No Native worker or controller remains active; do not resume this run.

## Audit and remaining decision

Evidence root: `C:\work\sah`, relative collection `.studii/cache-reuse/`. `cache-plan-v1.json` and `cache-plan-v2.json` pin the distinct playing inputs. `output/catalog-grade.json`, `output/transfer-grade.json`, `output/changed-grade.json` and `output/cost-grade.json` retain the original observations. `output/admission-v2.json` distinguishes actual v2 checks from inherited evidence and retains the moderate loss. The aborted campaign, initial standard-FEN validation failure and original occupancy failure remain visible.

The corrected run is closed at the requested 100-game checkpoint, short of the predeclared 100-pair/200-game comparison. The same typed-bound parent with reuse disabled remains the intended comparator. Do not estimate Delta Elo from the partial, asymmetrically paired prefix, inherit an earlier child's Elo, or claim Android/production qualification. Rating journals without complete Lite hints/final review remain excluded from learning admission. A fresh, explicitly authorized cohort would be needed to finish the 100-pair comparison.

The separate [training-060 curation](training-060-curation.md) closes a curriculum defect while preserving the original ID/progress and +200 objective. Its longer line does not rewrite this experiment's frozen catalog or establish stronger Native play.
