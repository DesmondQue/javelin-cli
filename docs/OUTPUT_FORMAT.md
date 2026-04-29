# Output Format

Javelin produces two output formats depending on the granularity setting (`-g`): **statement-level** (default) and **method-level**.

## Statement-Level CSV (default)

The default CSV output contains one row per executable line:

| Column | Type | Description |
|---|---|---|
| `FullyQualifiedClass` | string | Fully qualified Java class name |
| `LineNumber` | int | Line number in the source file |
| `OchiaiScore` | float | Suspiciousness score (0.0 – 1.0), formatted to 6 decimal places |
| `Rank` | float | Rank value (dense or average, formatted to 1 decimal place). Dense produces integer-like values (1.0, 2.0); average produces fractional values (1.5, 2.5). |

Example:

Example (dense ranking, default):

```csv
FullyQualifiedClass,LineNumber,OchiaiScore,Rank
com.example.Calculator,42,1.000000,1.0
com.example.Calculator,38,0.707107,2.0
com.example.MathHelper,15,0.500000,3.0
```

Example (average ranking, `--ranking average`):

```csv
FullyQualifiedClass,LineNumber,OchiaiScore,Rank
com.example.Calculator,42,1.000000,1.0
com.example.Calculator,38,0.707107,2.5
com.example.Calculator,50,0.707107,2.5
com.example.MathHelper,15,0.500000,4.0
```

## Method-Level CSV (`-g method`)

When `-g method` is specified, the CSV output contains one row per method:

| Column | Type | Description |
|---|---|---|
| `FullyQualifiedClass` | string | Fully qualified Java class name |
| `MethodName` | string | Method name (e.g., `search`, `<init>` for constructors) |
| `Descriptor` | string | JVM method descriptor for overload disambiguation (e.g., `(II)I`) |
| `MaxScore` | float | Maximum suspiciousness score among all lines in the method (6 decimal places) |
| `Rank` | float | Rank value (dense or average, formatted to 1 decimal place) |
| `FirstLine` | int | First line number of the method |
| `LastLine` | int | Last line number of the method |

Example (dense ranking):

```csv
FullyQualifiedClass,MethodName,Descriptor,MaxScore,Rank,FirstLine,LastLine
com.example.Calculator,compute,(II)I,1.000000,1.0,10,25
com.example.Calculator,validate,(I)Z,0.707107,2.0,30,45
com.example.MathHelper,sqrt,(D)D,0.500000,3.0,5,15
```

Example (average ranking, `--ranking average`):

```csv
FullyQualifiedClass,MethodName,Descriptor,MaxScore,Rank,FirstLine,LastLine
com.example.Calculator,compute,(II)I,1.000000,1.0,10,25
com.example.Calculator,validate,(I)Z,0.707107,2.5,30,45
com.example.Calculator,parse,(Ljava/lang/String;)I,0.707107,2.5,50,70
com.example.MathHelper,sqrt,(D)D,0.500000,4.0,5,15
```

Lines not contained in any method (field initializers, static blocks) are grouped under a synthetic `<class-level>` entry.

## Terminal Summary

### Statement-Level

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
+------+------------+-------+---------+----------------------------------------------+

  * Top-N = cumulative lines to inspect at each rank (for Top-N evaluation).
```

### Method-Level

When `-g method` is specified, the terminal summary groups results by method instead of line:

```
+===============================================================+
|  Analysis Complete (Method-Level)                              |
+===============================================================+

Ranking Overview:

  Total methods ranked:     109
  Methods with score > 0:   30
  Distinct rank groups:     12

Suspiciousness Ranking (all groups with score > 0):

+--------+------------+---------+---------+----------------------------------------------+
|   Rank | Score      | Methods | Top-N   | Top Methods                                  |
+--------+------------+---------+---------+----------------------------------------------+
|    1.0 |     0.5774 |       2 |       2 | Builder#build, SearchQuery#create             |
|    3.0 |     0.4714 |       1 |       3 | WildcardSearchQuery#execute                   |
|    4.0 |     0.3536 |       3 |       6 | VersionProvider#getVersion, +2 more           |
+--------+------------+---------+---------+----------------------------------------------+

  * Top-N = cumulative methods to inspect at each rank.
```

### Column Guide

#### Statement-Level Columns

| Column | Description |
|---|---|
| **Rank** | Dense or average rank (depending on `--ranking`). |
| **Score** | Ochiai (or Ochiai-MS) suspiciousness score for this tier. |
| **Lines** | Number of lines sharing this exact score. |
| **Top-N** | Cumulative lines inspected up to and including this rank. |
| **Top Classes** | Dominant classes in the group, sorted by line count. |

#### Method-Level Columns

| Column | Description |
|---|---|
| **Rank** | Dense or average rank (depending on `--ranking`). |
| **Score** | Maximum Ochiai (or Ochiai-MS) score among the method's lines. |
| **Methods** | Number of methods sharing this exact score/rank. |
| **Top-N** | Cumulative methods inspected up to and including this rank. |
| **Top Methods** | Method names in the group, shown as `ClassName#methodName`. |

### Ranking Overview Metrics

| Metric | Description |
|---|---|
| **Total lines/methods** | All executable elements discovered by coverage analysis. |
| **With score > 0** | Elements covered by at least one failing test (candidates for the fault). |
| **Distinct groups** | Number of unique suspiciousness tiers. Fewer groups means more ties. |
| **Uniqueness** *(statement only)* | Ratio of distinct groups to total lines (higher = less ambiguity). |

## Tie-heavy Rankings

Lines (or methods) that share identical coverage profiles receive the same suspiciousness score. This is inherent to all SBFL techniques, not specific to Javelin.

Two ranking strategies are available via `--ranking` at both granularity levels:

- **Dense** (default, recommended for debugging): ties share the same rank, next rank is the next integer. Example: `1.0, 2.0, 2.0, 3.0`.
- **Average (MID)** (for evaluation): ties receive the mean of their ordinal positions. Example: `1.0, 2.5, 2.5, 4.0`. This is the standard ranking used in SBFL evaluation literature (Pearson et al. ICSE 2017, Sarhan & Beszedes 2023) for computing Top-N and EXAM scores.
