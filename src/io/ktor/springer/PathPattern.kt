package io.ktor.springer

class PathPattern(val pathPattern: String) {
    companion object {
        val PARAM_REGEX = Regex("\\{(\\w*)\\}")
    }

    val pathNames by lazy { PARAM_REGEX.findAll(pathPattern).map { it.groupValues[1] }.toList() }
    val pathRegex by lazy { Regex(replace { "(\\w+)" }) }

    fun replace(replacer: (String) -> String): String {
        return pathPattern.replace(PARAM_REGEX) { mr -> replacer(mr.groupValues[1] )}
    }

    fun extract(path: String): List<String> {
        return pathRegex.find(path)?.groupValues?.drop(1) ?: listOf()
    }
}