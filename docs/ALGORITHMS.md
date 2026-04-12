# Algorithms

Javelin supports two fault localization algorithms.

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

## Ochiai-MS (Mutation Score weighted) — ⚠️ Experimental

> **This algorithm is experimental.** It is a novel research contribution exploring the integration of mutation testing into SBFL. Results may differ from standard Ochiai and the approach is under active evaluation.

Enhanced variant that runs scoped [PITest](https://pitest.org/) mutation analysis on the fault region (lines covered by failing tests), then weights each passing test by its mutation-killing strength. This penalizes weak passing tests and rewards strong ones.

```bash
javelin -a ochiai-ms -t build/classes/java/main -T build/classes/java/test -s src/main/java -o results.csv
```

> **Note:** `ochiai-ms` requires the `-s/--source` flag and takes longer due to mutation analysis.
