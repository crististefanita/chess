# Native Hard copies from main: results and limits

## Overview

[A/B and Elo protocol](hard-ab-elo.md) · [Current feedback](../feedback/hard.md)

This round's baseline is `65a29ab023a6a1da11a0da1b3476da717bee5baf`. The public selector is `LEGACY`; its existing adaptive configuration allows 100k–200k additional nodes. This counter excludes the shared H3 search. Repertoire and policies remain identical between the parent and each copy.

## Speed

Three mechanisms were tested separately, each starting from the same main. They are not combined.

| Copy | Change | Aggregate process CPU | Aggregate thread CPU | Forks with both CPU measures lower | Verdict |
|---|---|---:|---:|---|---|
| A1 | Legality shortcut outside every king ray | −1.50% | −0.92% | Criterion fails | Not qualified for speed. |
| A2 | King-pin identification with bit masks | −2.01% | −1.98% | Criterion fails | Not qualified for speed. |
| A3 | Exact key packing from occupied-square bits | **−15.51%** | **−15.81%** | **4/4** | Initial qualification on the measured set. |

For A3, aggregate elapsed time falls 16.46% in all four forks. Allocations remain effectively unchanged, +0.014%; this is not a memory-reduction claim. Each probe uses four fresh serial JVMs, 64 warmup and 64 measured observations, with alternating order. Builds and the Stockfish oracle do not run during timing. These are JVM results on one host and four timing contexts, not Android performance qualification or Elo gain.

The parent profile identified search-key construction as the first Native frame in 93 of 501 CPU samples. A3 avoids packing zero to 62 empty squares: it visits `occupied` with `Long.numberOfTrailingZeros` and `bits &= bits - 1`. The four key words remain exact, without probabilistic hashing or omitted state. Turn, castling and en passant remain included. This is a refactor, not a playing policy.

`SearchKeyPackingTest` independently compares previous packing across all 64 squares, more than 1,800 states, promotions, captures and special rights. Focused suites retain the independent legality oracle, perft, cache/PVS and Stockfish frontier. Native parity compares the chosen move, every ordered PV, depth, full state and original counter; equal node counts alone are insufficient.

## A3 confirmation

Later confirmation on four dense contexts in four additional serial JVMs failed: process CPU +8.85%, thread CPU +11.23%, elapsed +10.06%; only 2/4 processes had lower process CPU and 1/4 lower thread CPU. The 72 identical pairs remain correctness evidence, but the initial speed finding does not generalize. A3 stays experimental and is not promoted as a general optimization. New evidence is under `.studii/native-hard-next/confirmation/` relative to the same external root.

## Playing strength

B tries a ±200 cp window for the first root, predicted only from that root's exact value in the previous completed iteration. Fail-high/fail-low requires a full-window re-search within the same budget; tests compare with independent minimax, interruption and unknown YAML rejection. H6 vetoes remain unchanged.

The mechanism **does not repair Larsen**: the actual choice remains `f3`, H5, 100k additional nodes. Isolated same-horizon searches give H5 `f3` +24 cp versus `Nd3` +21 cp, but H6 −82 versus −20 cp. These probes budget each root separately; they are not complete 200k decisions. Stockfish 19 confirms `Nd3`; `f3` loses 465/403.5‰ WDL at H17/H19. The actual Stockfish process exit is retained.

On an exploratory 24-position set frozen before oracle scoring, B changes no moves, gains H6 once and loses it elsewhere. All 48 Basic/Advanced pairs remain identical. The set comes from 12 known families; it is neither a globally independent holdout nor a complete-game league. B remains experimental and unpromoted.

## Evidence and next step

### Later root probes

A narrow window for secondary roots preserves scores and all ties in independent minimax but changes the stopping point of bounded search. Across all 1,238 public first decisions, the baseline accepts 1,214 and the probe 1,213: no recovery and one loss in `training-012`. The probe reaches H5 and prefers `…h5`; its line ends at `…Qxf2` although `Kxf2` and `Qxf2` are legal. The baseline retains `…Rb1` from H4. Greater depth does not prove progress when evaluation precedes the completed exchange.

Stockfish 19 confirms `…Rb1` at H17/H19; `…h5` loses 502/503.5‰ WDL in a separate comparison with free replies. The receipt retains six phases, exact scores and the oracle's actual exit. The probe declares composed origin and empty history explicitly; it does not claim the original source game.

Both arms accept first decisions in all 500/300/200 mate-in-1/2/3 problems, 95/100 tactics and 24/24 intermezzos; training changes from 95/114 to 94/114. These are first choices, not certification of every line and defense or an Elo rating.

A second probe lets the H4 leader return only after a complete H6 comparison and an exact H4 recheck, without horizon credit. It experimentally repairs Larsen at 500k (`Nd3`) and the Caro–Kann study choice (`Rd1`). Stockfish H17/H19 confirms those moves, but the probe loses 500/498‰ WDL in `training-021` in both colors through `Qxf5+` instead of `Qg7+`. This veto generalization is therefore also unpromoted.

`NativeHorizonGuardTest` protects `training-012` and its color reflection. `hard-lessons/visible-exchanges.yaml` is test-only: its unfinished-exchange/verified-queen-sacrifice pair governs a future repair without injecting known moves into the engine.

External root: `C:\work\sah`; relative folder `.studii/native-hard-current/`. `README.md` contains the plan, `RESULTS.md` the verdict, and `publication/comparison-v2.html` retains Extent with imported original expected/actual/verdict assertions, Native receipts, Stockfish phases and negative controls. Originals are under `build/<copy>/initial/components.html`. Report import is not a new execution.

Retain FAIL for A1/A2 and objective B; do not conceal them behind passing components. The original JFR remains at `profile/parent.jfr`; streaming reads reproduce the aggregation. The redundant JSON stack export was removed.

Full Larsen tracing confirms two successive causes: adaptive stopping before the first H6 root at 100k and the veto retaining `f3` after a complete H6 comparison at 500k with `Nd3` leading. At 200k the comparison remains incomplete. The cost and corroboration probes were executed and rejected for the reasons above. The next priority is the demonstrated `training-012` tactical frontier with a sound sacrifice as counterexample, not another renamed root adjustment. A3 remains experimental after failed speed confirmation; combining it with a strength change requires another arm. `Delta Elo = NOT_MEASURED`. Stockfish Lite retains ownership of hints, the bar and final analysis; APK/RAG belongs to the delivery owner.

Final verification on the main copy without A3 passes 75/75 components and governance checks. Archive compatibility is repaired without changing the expected historical digest. Results, including failed experiments, are indexed under `C:\work\sah`, relative folder `.studii/native-hard-next/`. These checks precede later live synchronization and do not qualify changes added afterward.
