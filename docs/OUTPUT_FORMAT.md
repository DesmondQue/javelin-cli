# Output Format

## CSV Report

Javelin outputs a CSV file with the following columns:

| Column | Description |
|---|---|
| `class` | Fully qualified Java class name |
| `line` | Line number in the source file |
| `score` | Suspiciousness score (0.0 – 1.0) |
| `rank` | Dense rank (1 = most suspicious). Tied scores share the same rank. |

Example:

```csv
FullyQualifiedClass,LineNumber,OchiaiScore,Rank
com.example.Calculator,42,1.000000,1
com.example.Calculator,38,0.707107,2
com.example.MathHelper,15,0.500000,3
```

## Terminal Summary

After analysis, Javelin prints a ranking overview and a full suspiciousness table for all non-zero score groups. Each row represents a **score tier** (group of lines sharing the same suspiciousness score).

```
+===============================================================+
|  Analysis Complete                                            |
+===============================================================+

Ranking Overview:

  Total lines tracked:    341
  Lines with score > 0:   62
  Distinct score groups:  10
  Uniqueness (groups/lines): 2.93%

Suspiciousness Ranking (all groups with score > 0):

+------+------------+-------+---------+----------------------------------------------+
| Rank | Score      | Lines | Top-N   | Top Classes                                  |
+------+------------+-------+---------+----------------------------------------------+
|    1 |     0.5774 |     7 |       7 | WildcardSearchQuery$Builder (6), +1 more     |
|    2 |     0.4714 |     1 |       8 | WildcardSearchQuery                          |
|    3 |     0.3536 |    12 |      20 | ClasspathVersionProvider (5), +2 more        |
|    4 |     0.3430 |     2 |      22 | SearchQuery (2)                              |
|    5 |     0.2500 |    13 |      35 | ClassnameQuery$Builder (10), +3 more         |
|    6 |     0.2041 |     2 |      37 | ClassnameQuery$Builder (2)                   |
|    7 |     0.1443 |    23 |      60 | TabularOutputPrinter (23)                    |
|    8 |     0.1179 |     1 |      61 | SearchResponse$Response$Doc                  |
|    9 |     0.0945 |     1 |      62 | SearchResponse$Response                      |
+------+------------+-------+---------+----------------------------------------------+

  * Top-N = cumulative lines to inspect at each rank (for Top-N evaluation).
```

### Column Guide

| Column | Description |
|---|---|
| **Rank** | Dense rank — tied scores share the same rank. |
| **Score** | Ochiai (or Ochiai-MS) suspiciousness score for this tier. |
| **Lines** | Number of lines sharing this exact score. |
| **Top-N** | Cumulative lines inspected up to and including this rank. Use this for Top-N evaluation: if the buggy line is at rank *k*, the Top-N value tells you how many lines a developer would inspect in the worst case. |
| **Top Classes** | Dominant classes in the group, sorted by line count. The number in parentheses indicates how many lines from that class are in this tier. |

### Ranking Overview Metrics

| Metric | Description |
|---|---|
| **Total lines tracked** | All executable lines discovered by coverage analysis. |
| **Lines with score > 0** | Lines covered by at least one failing test (candidates for the fault). |
| **Distinct score groups** | Number of unique suspiciousness tiers. Fewer groups means more ties. |
| **Uniqueness** | Ratio of distinct groups to total lines (higher = less ambiguity between lines). |

## Tie-heavy Rankings

Lines that share identical coverage profiles (i.e., covered by exactly the same set of passing and failing tests) receive the same suspiciousness score. This is inherent to all SBFL techniques, not specific to Javelin. The terminal summary groups tied lines together and reports a Top-N metric showing how many lines a developer would need to inspect at each rank.
