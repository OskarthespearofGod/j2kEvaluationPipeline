# j2k-eval — Agentic Java-to-Kotlin Evaluation Pipeline

An automated GitHub Actions pipeline that runs IntelliJ's static `j2k` converter against a real-world Java codebase and evaluates conversion quality using a Kotlin-written evaluator.

## Target project

**RxJava 3.1.9** ([ReactiveX/RxJava](https://github.com/ReactiveX/RxJava)) — chosen because it is a large, production-grade library with complex generics, functional patterns, and no framework coupling that would skew results.

## Repository structure

```
.
├── .github/workflows/
│   └── j2k-eval.yml          # CI pipeline
├── converter/
│   └── run_j2k.sh            # Shell wrapper for headless j2k
├── evaluator/                # Kotlin project — evaluation logic
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── src/main/kotlin/eval/
│       ├── Main.kt
│       ├── Analyser.kt       # Per-file metrics and scoring
│       └── Reporters.kt      # JSON + Markdown report writers
├── edge-cases/src/           # Custom Java stress-test dataset
│   ├── NestedAnonymousClasses.java
│   ├── StaticInitializerBlock.java
│   ├── ComplexGenerics.java
│   ├── CheckedExceptionsAndResources.java
│   ├── SpringAnnotations.java
│   └── ComplexEnum.java
└── docs/
    └── EDGE_CASES.md         # Detailed hypothesis report
```

## How to reproduce locally

### Prerequisites

- JDK 17+
- IntelliJ IDEA Community 2024.3 installed at `$IDEA_HOME`
- Kotlin 2.0+ on PATH

### Steps

```bash
# 1. Clone this repo
git clone https://github.com/OskarthespearofGod/j2kEvaluationPipeline && cd j2kEvaluationPipeline

# 2. Clone the target Java project
git clone --depth=1 --branch 3.1.9 https://github.com/ReactiveX/RxJava target-repo

# 3. Run j2k on the target repo
mkdir -p converted/target
bash converter/run_j2k.sh ./target-repo/src/main/java ./converted/target $IDEA_HOME

# 4. Run j2k on the edge-case dataset
mkdir -p converted/edge-cases
bash converter/run_j2k.sh ./edge-cases/src ./converted/edge-cases $IDEA_HOME

# 5. Build and run the evaluator
cd evaluator
./gradlew shadowJar
cd ..

java -jar evaluator/build/libs/evaluator-all.jar \
  --original  target-repo/src/main/java \
  --converted converted/target \
  --report    results/target-report.json \
  --markdown  results/target-report.md

java -jar evaluator/build/libs/evaluator-all.jar \
  --original  edge-cases/src \
  --converted converted/edge-cases \
  --report    results/edge-report.json \
  --markdown  results/edge-report.md \
  --edge-mode

# 6. View results
cat results/target-report.md
cat results/edge-report.md
```

## Evaluation metrics

The evaluator (written entirely in Kotlin) measures the following per converted file:

| Metric | Description |
|--------|-------------|
| `conversion_succeeded` | j2k did not crash or time out |
| `no_unsafe_assertions` | No `!!` operators — null safety preserved |
| `no_getter_setter_survivors` | No Java-style `getX()`/`setX()` left in output |
| `line_count_reduced` | Kotlin output ≤ 105% lines of the Java input |
| `prefers_val_over_var` | `val` count ≥ `var` count (immutability) |
| `no_java_collection_imports` | No `java.util.ArrayList` / `HashMap` imports |
| `no_synthetic_accessors` | No `access$NNN` synthetic accessor patterns |

These combine into a **0–100 idiomatic score** per file.

## CI pipeline

The GitHub Actions workflow (`.github/workflows/j2k-eval.yml`):

1. Clones RxJava 3.1.9
2. Downloads IntelliJ IDEA Community (headless)
3. Runs `run_j2k.sh` on both the real-world repo and the edge-case dataset
4. Builds the Kotlin evaluator with Gradle
5. Runs both evaluation passes and publishes the Markdown reports as a GitHub Actions job summary
6. Uploads JSON + Markdown as build artifacts

See `docs/EDGE_CASES.md` for detailed hypotheses and expected results.
