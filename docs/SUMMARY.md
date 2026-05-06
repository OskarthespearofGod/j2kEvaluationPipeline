# Pipeline Summary & Evaluation Results

## What the pipeline does

1. **Clones RxJava 3.1.9** — a real-world, production-grade Java library chosen for its complex generics, functional patterns, and significant codebase size (~500 Java files). Deliberately chosen over `spring-petclinic` to avoid framework-specific bias.

2. **Runs IntelliJ's static `j2k` converter** in headless mode against all Java source files. Files that fail to convert (timeout or crash) are logged as stubs and counted as failures.

3. **Runs the same converter on the edge-case dataset** — 6 hand-crafted Java files each targeting a specific j2k weakness.

4. **Runs the Kotlin evaluator** (`evaluator/`) against both outputs, producing a JSON report and a Markdown summary. The evaluator is written entirely in Kotlin as required.

5. **Posts results** as a GitHub Actions job summary and uploads them as build artifacts.

---

## Evaluation methodology

The evaluator measures 7 checks per file and combines them into a 0–100 idiomatic score:

- **Conversion success** (40 pts): did j2k produce actual Kotlin, or did it crash?
- **Null safety** (–2 per `!!`, up to –20): unsafe non-null assertions indicate the converter couldn't infer nullability
- **Idiomatic style** (up to –40): getter/setter survivors, `var` over `val`, redundant Java collection imports, synthetic accessors, line count growth

This is a **heuristic evaluation** — it does not compile the output. A future improvement would be to run `kotlinc` on the output and report compilation errors as a separate metric.

---

## Real-world results (RxJava 3.1.9) — expected findings

These are predictions based on known j2k behaviour, to be verified by the actual pipeline run:

| Metric | Expected value |
|--------|---------------|
| Conversion success rate | ~85–90% |
| Files with `!!` operators | ~60–70% |
| Avg `!!` per file | ~3–8 |
| Getter/setter survivors | ~40–50% of files |
| Avg idiomatic score | ~55–65 / 100 |

The most common failure modes in a library like RxJava are expected to be:
- **Null safety loss**: RxJava uses `@NonNull` / `@Nullable` annotations that j2k partially respects but often misses in generic callbacks
- **Functional interface conversion**: RxJava's many single-method interfaces should become lambdas, but complex ones (with generics or default methods) likely stay as `object` expressions
- **Static nested classes**: RxJava uses many package-private static nested classes that j2k moves to companion objects, sometimes incorrectly

---

## Edge-case results — expected findings

| File | Predicted outcome | Score |
|------|-----------------|-------|
| `NestedAnonymousClasses` | Converts but inner comparator stays as object | ~65 |
| `StaticInitializerBlock` | `static {}` partially converted; `companion object init {}` may not compile | ~40 |
| `ComplexGenerics` | Wildcards mostly OK; multiple-bound drops `Cloneable` | ~70 |
| `CheckedExceptionsAndResources` | Single `.use` ✓; multi-resource nesting verbose | ~75 |
| `SpringAnnotations` | Missing `open`; `@Value` on `Int` broken | ~30 |
| `ComplexEnum` | Kotlin enums support abstract methods; likely passes | ~85 |

---

## Known limitations of this pipeline

1. **No compilation check**: The evaluator uses regex/AST heuristics rather than actually compiling the output with `kotlinc`. Adding a compile step would require resolving dependencies, which is complex for a large library like RxJava.

2. **Headless j2k requires type resolution**: The static converter works best when it can resolve symbols. Running it against individual files without a full classpath reduces quality. The pipeline builds the project first, but symbol resolution across the full dependency graph is still incomplete.

3. **No gold Kotlin comparison**: For RxJava specifically, there is no official Kotlin port to compare against. The evaluation is therefore heuristic only. A future improvement would be to use a project that has both a Java and Kotlin version.
