# Edge-Case Dataset — Hypotheses & Expected Results

This document describes each file in the `edge-cases/src/` dataset, the hypothesis being tested, and what I expect the static `j2k` converter to produce (or fail to produce).

---

## EC-001 · `NestedAnonymousClasses.java`

**Hypothesis:** j2k will correctly convert SAM-compatible anonymous classes to lambdas, but will fail or produce non-idiomatic output for anonymous classes with fields or state.

**What I expect j2k to do:**
- `buildPredicate` → `Predicate { s -> s.length >= minLength }` (correct SAM lambda)
- `buildTransformer` → an `object : Transformer { ... }` expression, but the inner `sorter` anonymous `Comparator` may be left as-is rather than converted to `compareBy { it.length }`

**Expected failure point:** The nested anonymous `Comparator` with a field inside the outer anonymous class. j2k tends to produce an `object` expression with a `val sorter = object : Comparator<String> { ... }` block rather than the idiomatic `compareBy`.

**Idiomatic Kotlin target:**
```kotlin
fun buildTransformer(prefix: String): Transformer = object : Transformer {
    private val sorter = compareBy<String> { it.length }
    override fun transform(input: String) = "${prefix}_${input.lowercase()}"
}

fun buildPredicate(minLength: Int): (String) -> Boolean = { it.length >= minLength }
```

---

## EC-002 · `StaticInitializerBlock.java`

**Hypothesis:** j2k will move static fields to a `companion object`, but static initializer blocks (`static { ... }`) with complex logic will either be dropped or inlined incorrectly.

**What I expect j2k to do:**
- `APP_NAME` → `companion object { const val APP_NAME = "j2k-eval" }` ✓
- `instanceCount` → `companion object { var instanceCount = 0 }` ✓
- `PRIORITY_MAP` with `static { }` → likely a `@JvmStatic val PRIORITY_MAP` initialized inline, but may lose the `unmodifiableMap` wrapper
- Nested `Registry.INSTANCE` singleton → `companion object` with `init { }` block, possibly correct

**Expected failure point:** The `static { }` block initializing `PRIORITY_MAP`. j2k may produce a `companion object { init { } }` that references `m` before it's declared in scope, causing a compile error.

**Idiomatic Kotlin target:**
```kotlin
companion object {
    const val APP_NAME = "j2k-eval"
    var instanceCount = 0
    val PRIORITY_MAP: Map<String, Int> = mapOf("LOW" to 1, "MEDIUM" to 5, "HIGH" to 10)
}
```

---

## EC-003 · `ComplexGenerics.java`

**Hypothesis:** j2k will incorrectly translate bounded wildcards and multiple-bound generics, producing either `!!` assertions or `Any?` where specific types are expected.

**What I expect j2k to do:**
- `List<? extends Number>` → `List<out Number>` ✓ (likely correct)
- `List<? super T>` → `List<in T>` ✓ (likely correct)
- `<T extends Comparable<T> & Cloneable>` → **unknown** — Kotlin doesn't support intersection types at call site; j2k may produce `T : Comparable<T>` and silently drop `Cloneable`
- Raw `List` → `List<*>` or `MutableList<Any?>` with an unchecked cast

**Expected failure point:** The multiple-bound generic `<T extends Comparable<T> & Cloneable>`. Kotlin has no direct equivalent; the idiomatic fix is `where T : Comparable<T>, T : Cloneable` which j2k is unlikely to produce.

**Idiomatic Kotlin target:**
```kotlin
fun <T> findMax(items: List<T>): T? where T : Comparable<T> = items.maxOrNull()
```

---

## EC-004 · `CheckedExceptionsAndResources.java`

**Hypothesis:** j2k will convert single-resource `try-with-resources` to `.use { }` reliably, but multi-resource `try` will produce nested `.use` blocks that are syntactically valid but less readable than the Java original.

**What I expect j2k to do:**
- `readFile` → `BufferedReader(FileReader(path)).use { ... }` ✓
- `copyFile` (two resources) → likely nested `.use` calls; may not correctly handle the inner resource closing before the outer
- `@Throws` annotations may or may not be emitted on the converted functions
- Checked exceptions in signatures will be silently dropped (correct for Kotlin)

**Expected failure point:** Multi-resource `try` in `copyFile`. The idiomatic Kotlin is:
```kotlin
FileInputStream(src).use { input ->
    FileOutputStream(dst).use { output ->
        input.copyTo(output)
    }
}
```
j2k will likely not use `copyTo` and may produce an off-by-one on the buffer loop.

---

## EC-005 · `SpringAnnotations.java`

**Hypothesis:** j2k will produce `class` (not `open class`) for Spring beans, breaking proxy-based AOP at runtime. Field injection with `@Value` will produce `lateinit var` inconsistently.

**What I expect j2k to do:**
- `@Service` class → `class UserServiceImpl` (missing `open`) ← **this is the main bug**
- `@Value` fields → `@Value(...) private lateinit var appName: String` — likely correct
- `@Value` on `Int` field → **may fail** since `lateinit` only works on non-primitive types; j2k may produce `var maxUsers: Int = 0` ignoring the `@Value` injection
- `@RestController` / `@RequestMapping` should be preserved as-is

**Expected failure point:** The `@Value("${app.max-users:100}") private int maxUsers` field. Kotlin `Int` cannot be `lateinit`, so j2k will likely either drop the annotation or produce invalid code.

**Proposed fix:** Emit `@field:Value("...")` in Kotlin (use-site annotation targeting), and flag the missing `open` as a known limitation.

---

## EC-006 · `ComplexEnum.java`

**Hypothesis:** j2k cannot correctly convert enums with per-constant abstract method overrides. Kotlin enums do not support anonymous class bodies per constant in the same way Java does.

**What I expect j2k to do:**
- Simple enum constants → converted correctly
- `abstract fun escalate()` + per-constant override → j2k likely produces an `enum class` with abstract members, which Kotlin *does* support — so this may actually pass
- `abstract fun isUrgent(): Boolean` → same

**Expected result:** This may be one of the edge cases that j2k handles reasonably, since Kotlin enums *do* allow abstract methods. The failure risk is in the per-constant body syntax — Kotlin uses `LOW { override fun escalate() = MEDIUM }` which j2k should produce.

**Actual Kotlin target:**
```kotlin
enum class ComplexEnum(val value: Int, val description: String) {
    LOW(1, "low priority") {
        override fun escalate() = MEDIUM
        override fun isUrgent() = false
    },
    // ...
    ;
    abstract fun escalate(): ComplexEnum
    abstract fun isUrgent(): Boolean

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value }
            ?: error("Unknown value: $value")
    }
}
```

---

## Summary table (expected)

| File | Expected outcome | Primary risk |
|------|-----------------|--------------|
| `NestedAnonymousClasses` | Partial pass | Inner anonymous class not converted to lambda |
| `StaticInitializerBlock` | Partial pass | `static { }` block may produce invalid scoping |
| `ComplexGenerics` | Partial pass | Multiple-bound generics lose `Cloneable` constraint |
| `CheckedExceptionsAndResources` | Pass (functional) | Multi-resource `.use` nesting is verbose |
| `SpringAnnotations` | Fail (semantic) | Missing `open`, broken `@Value` on primitive |
| `ComplexEnum` | Pass | Kotlin enums support abstract methods |
