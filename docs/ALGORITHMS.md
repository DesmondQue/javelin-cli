# Algorithms

Javelin supports two fault localization algorithms, with configurable output granularity and ranking strategies.

## Ochiai (default)

Standard SBFL algorithm. Computes a suspiciousness score for each executable line based on how frequently it is covered by failing vs. passing tests.

$$\text{Ochiai}(s) = \frac{e_f(s)}{\sqrt{n_f \cdot (e_f(s) + e_p(s))}}$$

Where:
- $e_f(s)$ = number of failing tests that execute statement $s$
- $e_p(s)$ = number of passing tests that execute statement $s$
- $n_f$ = total number of failing tests

```bash
javelin -a ochiai -t build/classes/java/main -T build/classes/java/test -o report.csv
```

## Ochiai-MS (Mutation Score weighted) -- ⚠️ Experimental

> **This algorithm is experimental.** It is a novel research contribution exploring the integration of mutation testing into SBFL. Results may differ from standard Ochiai and the approach is under active evaluation.

Enhanced variant that runs scoped [PITest](https://pitest.org/) mutation analysis on the fault region (lines covered by failing tests), then weights each passing test by its mutation-killing strength. This penalizes weak passing tests and rewards strong ones.

```bash
javelin -a ochiai-ms -t build/classes/java/main -T build/classes/java/test -s src/main/java -o results.csv
```

> **Note:** `ochiai-ms` requires the `-s/--source` flag and takes longer due to mutation analysis.

## Method-Level Aggregation

Scoring always happens at the statement (line) level. When `-g method` is specified, Javelin aggregates line-level scores to method-level as a post-scoring step. This mirrors the evaluation methodology used by GZoltar and standard SBFL literature (Defects4J benchmarks, Sarhan & Beszedes 2020).

**How it works:**

1. Method boundaries are extracted from JaCoCo's `IMethodCoverage` data (name, descriptor, first/last line).
2. Each scored line is mapped to its containing method via line range lookup.
3. Each method's suspiciousness score is the **maximum** score among its lines.
4. Methods are sorted by score (descending) and ranked.
5. Lines not contained in any method (e.g., field initializers, static blocks) are grouped under a synthetic `<class-level>` entry.

Overloaded methods are distinguished by their JVM descriptor (e.g., `add(II)I` vs. `add(DD)D`). Synthetic methods (lambdas, bridge methods) and abstract/native methods are excluded.

```bash
javelin -t build/classes/java/main -T build/classes/java/test -o report.csv -g method
```

## Ranking Strategies

Javelin supports two ranking strategies, selectable via `--ranking`:

### Dense Ranking (default, recommended)

Tied scores share the same rank. The next distinct score gets the next integer rank.

Example: scores `[1.0, 0.7, 0.7, 0.3]` → ranks `[1, 2, 2, 3]`

This is the default and recommended strategy for interactive debugging. It produces clear integer-like ranks that tell the developer "look here first."

### Average Ranking (MID) -- for evaluation

Tied scores receive the average of the positions they would occupy. This is the standard ranking used in SBFL evaluation literature (Pearson et al., ICSE 2017; Sarhan & Beszedes 2023) for computing EXAM scores and Top-N metrics.

$$\text{MID}(s) = S + \frac{E - 1}{2}$$

Where:
- $S$ = 1-based start position of the tie group
- $E$ = number of elements sharing the same score

Example: scores `[1.0, 0.7, 0.7, 0.3]` → ranks `[1.0, 2.5, 2.5, 4.0]`

Average ranking is available with both granularity levels:

```bash
# Statement-level with average ranking (for EXAM score computation)
javelin -t build/classes/java/main -T build/classes/java/test -o report.csv --ranking average

# Method-level with average ranking (matches Sarhan & Beszedes methodology)
javelin -t build/classes/java/main -T build/classes/java/test -o report.csv -g method --ranking average
```

### When to use which

| Use case | Recommended settings |
|---|---|
| Debugging (finding the fault) | `-g statement` (default) + `--ranking dense` (default) |
| SBFL evaluation (EXAM scores, Top-N) | `--ranking average` at either granularity level |
| Reproducing Sarhan & Beszedes results | `-g method --ranking average` |
| Reproducing Pearson et al. (ICSE 2017) | `-g statement --ranking average` |
