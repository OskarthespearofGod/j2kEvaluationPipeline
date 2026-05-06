package eval

import java.io.File

// ── JSON output ───────────────────────────────────────────────────────────────

fun writeJson(summary: Summary, results: List<FileResult>, out: File) {
    val sb = StringBuilder()
    sb.appendLine("{")
    sb.appendLine("  \"summary\": {")
    sb.appendLine("    \"totalFiles\": ${summary.totalFiles},")
    sb.appendLine("    \"conversionFailures\": ${summary.conversionFailures},")
    sb.appendLine("    \"compiledCount\": ${summary.compiledCount},")
    sb.appendLine("    \"avgIdiomaticScore\": ${"%.2f".format(summary.avgIdiomaticScore)},")
    sb.appendLine("    \"avgLineRatio\": ${"%.3f".format(summary.avgLineRatio)},")
    sb.appendLine("    \"totalNullBangs\": ${summary.totalNullBangs},")
    sb.appendLine("    \"totalGetterSetters\": ${summary.totalGetterSetters}")
    sb.appendLine("  },")
    sb.appendLine("  \"files\": [")
    results.forEachIndexed { i, r ->
        sb.appendLine("    {")
        sb.appendLine("      \"java\": \"${r.javaPath.jsonEsc()}\",")
        sb.appendLine("      \"kotlin\": \"${r.ktPath.jsonEsc()}\",")
        sb.appendLine("      \"conversionFailed\": ${r.conversionFailed},")
        sb.appendLine("      \"idiomaticScore\": ${r.idiomaticScore},")
        sb.appendLine("      \"lineRatio\": ${"%.3f".format(r.lineRatio)},")
        sb.appendLine("      \"nullBangs\": ${r.nullBangCount},")
        sb.appendLine("      \"getterSetters\": ${r.getterSetterCount},")
        sb.appendLine("      \"checks\": [")
        r.checks.forEachIndexed { ci, c ->
            sb.append("        { \"name\": \"${c.name}\", \"passed\": ${c.passed}, \"detail\": \"${c.detail.jsonEsc()}\" }")
            if (ci < r.checks.lastIndex) sb.append(",")
            sb.appendLine()
        }
        sb.appendLine("      ]")
        sb.append("    }")
        if (i < results.lastIndex) sb.append(",")
        sb.appendLine()
    }
    sb.appendLine("  ]")
    sb.append("}")
    out.writeText(sb.toString())
}

private fun String.jsonEsc() = replace("\\", "\\\\").replace("\"", "\\\"")

// ── Markdown output ───────────────────────────────────────────────────────────

fun writeMarkdown(summary: Summary, results: List<FileResult>, out: File, edgeMode: Boolean) {
    val title = if (edgeMode) "Edge-Case Dataset Evaluation" else "Real-World Repo Evaluation"
    val sb = StringBuilder()

    sb.appendLine("# J2K Evaluation Report — $title")
    sb.appendLine()
    sb.appendLine("## Summary")
    sb.appendLine()
    sb.appendLine("| Metric | Value |")
    sb.appendLine("|--------|-------|")
    sb.appendLine("| Files analysed | ${summary.totalFiles} |")
    sb.appendLine("| Conversion failures | ${summary.conversionFailures} |")
    sb.appendLine("| Successfully converted | ${summary.compiledCount} |")
    sb.appendLine("| Avg idiomatic score | ${"%.1f".format(summary.avgIdiomaticScore)} / 100 |")
    sb.appendLine("| Avg Kotlin/Java line ratio | ${"%.3f".format(summary.avgLineRatio)} |")
    sb.appendLine("| Total `!!` (unsafe assertions) | ${summary.totalNullBangs} |")
    sb.appendLine("| Total Java-style getter/setter survivors | ${summary.totalGetterSetters} |")
    sb.appendLine()

    sb.appendLine("## Check legend")
    sb.appendLine()
    sb.appendLine("| Check | What it measures |")
    sb.appendLine("|-------|-----------------|")
    sb.appendLine("| `conversion_succeeded` | j2k did not crash or produce a stub |")
    sb.appendLine("| `no_unsafe_assertions` | No `!!` operators — null safety preserved |")
    sb.appendLine("| `no_getter_setter_survivors` | No Java-style `getX()`/`setX()` left |")
    sb.appendLine("| `line_count_reduced` | Kotlin output ≤ 105% of Java input (idiomatic code is shorter) |")
    sb.appendLine("| `prefers_val_over_var` | Immutability: `val` count ≥ `var` count |")
    sb.appendLine("| `no_java_collection_imports` | No `java.util.ArrayList` / `HashMap` imports (use Kotlin equivalents) |")
    sb.appendLine("| `no_synthetic_accessors` | No `access\$NNN` synthetic accessor patterns |")
    sb.appendLine()

    // Worst files by idiomatic score
    val worst = results.filter { !it.conversionFailed }
        .sortedBy { it.idiomaticScore }
        .take(10)

    sb.appendLine("## Worst-scoring files (top 10)")
    sb.appendLine()
    sb.appendLine("| File | Score | Null `!!` | Getter/setters | Line ratio |")
    sb.appendLine("|------|-------|-----------|----------------|------------|")
    worst.forEach { r ->
        val name = File(r.javaPath).name
        sb.appendLine("| `$name` | ${r.idiomaticScore} | ${r.nullBangCount} | ${r.getterSetterCount} | ${"%.2f".format(r.lineRatio)} |")
    }
    sb.appendLine()

    // Failed conversions
    val failed = results.filter { it.conversionFailed }
    if (failed.isNotEmpty()) {
        sb.appendLine("## Conversion failures (${failed.size} files)")
        sb.appendLine()
        failed.forEach { r ->
            sb.appendLine("- `${File(r.javaPath).name}`")
        }
        sb.appendLine()
    }

    if (edgeMode) {
        sb.appendLine("## Edge-case pass/fail breakdown")
        sb.appendLine()
        sb.appendLine("| File | Score | Failed checks |")
        sb.appendLine("|------|-------|--------------|")
        results.sortedBy { it.idiomaticScore }.forEach { r ->
            val failedChecks = r.checks.filter { !it.passed }.joinToString(", ") { it.name }
            sb.appendLine("| `${File(r.javaPath).name}` | ${r.idiomaticScore} | ${failedChecks.ifEmpty { "—" }} |")
        }
        sb.appendLine()
    }

    sb.appendLine("---")
    sb.appendLine("*Generated by j2k-eval evaluator — evaluation logic written in Kotlin*")

    out.writeText(sb.toString())
}
