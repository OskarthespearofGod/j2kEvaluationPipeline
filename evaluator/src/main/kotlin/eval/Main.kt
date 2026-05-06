package eval

import java.io.File

fun main(args: Array<String>) {
    val argMap = parseArgs(args)

    val originalDir = File(argMap["--original"] ?: error("--original required"))
    val convertedDir = File(argMap["--converted"] ?: error("--converted required"))
    val reportJson   = File(argMap["--report"]   ?: "results/report.json")
    val reportMd     = File(argMap["--markdown"] ?: "results/report.md")
    val edgeMode     = argMap.containsKey("--edge-mode")

    reportJson.parentFile.mkdirs()
    reportMd.parentFile.mkdirs()

    println("=== J2K Evaluator ===")
    println("Original : $originalDir")
    println("Converted: $convertedDir")
    println("Edge mode: $edgeMode")

    val manifest = readManifest(convertedDir)
    val pairs    = collectFilePairs(originalDir, convertedDir)

    println("Analysing ${pairs.size} converted file pairs...")

    val fileResults = pairs.map { (javaFile, ktFile) ->
        analyseFilePair(javaFile, ktFile, edgeMode)
    }

    val summary = buildSummary(manifest, fileResults, edgeMode)

    writeJson(summary, fileResults, reportJson)
    writeMarkdown(summary, fileResults, reportMd, edgeMode)

    println("\n── Summary ──────────────────────────────────")
    println("Files analysed     : ${summary.totalFiles}")
    println("Conversion failures: ${summary.conversionFailures}")
    println("Compiled (est.)    : ${summary.compiledCount}")
    println("Avg idiomatic score: ${"%.2f".format(summary.avgIdiomaticScore)} / 100")
    println("Report written to  : ${reportMd.absolutePath}")
}

fun parseArgs(args: Array<String>): Map<String, String> {
    val map = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        if (args[i].startsWith("--")) {
            if (i + 1 < args.size && !args[i + 1].startsWith("--")) {
                map[args[i]] = args[i + 1]
                i += 2
            } else {
                map[args[i]] = ""
                i++
            }
        } else i++
    }
    return map
}
