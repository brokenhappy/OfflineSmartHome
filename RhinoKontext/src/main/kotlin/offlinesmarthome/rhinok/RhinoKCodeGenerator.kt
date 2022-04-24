package offlinesmarthome.rhinok

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver
import java.io.Reader
import java.util.regex.Pattern

class RhinoKCodeGenerator {
    class IllegalYamlFile(message: String) : Exception(message)

    @JvmInline
    private value class YamlNode(val yaml: Map<String, *>) {
        fun nextMap(key: String, messageProvider: () -> String): YamlNode =
            nextMapOrNull(key) ?: throw IllegalYamlFile(messageProvider())

        fun nextMapOrNull(key: String): YamlNode? =
            (yaml[key] as? Map<String, *>)?.let(::YamlNode)

        fun asStringMultiMapEntries(): Sequence<Pair<String, List<String>>> =
            yaml.asSequence().map { (key, value) -> key to (value as List<*>).map { it.toString() } }
    }

    fun fromRhinoYamlExport(yaml: Reader): CharSequence {
        val context = readRhinoContext(yaml)
        return buildString {
            appendLine("""
                import ai.picovoice.rhino.RhinoInference
                
                sealed interface Intent {
                    object NotUnderstood : Intent
            """.trimIndent())
            context.intents.forEach { intent ->
                if (intent.variables.isEmpty()) {
                    appendLine("    object ${intent.name} : Intent")
                } else {
                    append("    data class ")
                    append(intent.name)
                    intent.variables.joinTo(this, prefix = "(", postfix = ") : Intent\n") {
                        "val " + it.name + ": " + it.slotType.name + if (it.isRequired) "" else "?"
                    }
                }
            }
            appendLine("""
           |    companion object {
           |        fun from(inference: RhinoInference): Intent {
           |            if (!inference.isUnderstood)
           |                return NotUnderstood
           |            class IllegalInferenceException(reason: String): Exception(""${'"'}
           |                Illegal inference, reason: '${'$'}reason'. Based on Rhino's inferred: ${'$'}inference
           |                Please make sure that the .rhn file is created from the same yml as the Intents generated in Kotlin.
           |                If you are sure the set-up is correct, please file an issue
           |            ""${'"'}.trimIndent())
           |        
           |            val slots = inference.slots
           |            return when (inference.intent) {
            """.trimMargin())
            context.intents.forEach { intent ->
                append("                \"${intent.name}\" -> ${intent.name}")
                intent.variables.joinTo(this, prefix = "(", postfix = ")\n") {
                    "slots.${if (it.isRequired) "getRequiredSlot" else "getSlot"}(\"${it.name}\", ::IllegalInferenceException)"
                }
            }
//            "ChangeLightState" -> Intent.ChangeLightState(slots.getRequiredSlot("state", ::IllegalInferenceException))
            appendLine("""
           |                else -> throw IllegalInferenceException("Intent ${'$'}{inference.intent} is not a legal intent kind")
           |            }
           |        }
           |    }
            """.trimMargin())
            appendLine('}')
            context.slots.forEach { slot ->
                appendLine("enum class ${slot.name} {")
                slot.elements.forEach { appendLine("    $it,") }
                appendLine('}')
            }
            appendLine("""
                private inline fun <reified T: Enum<T>> Map<String, String>.getSlot(key: String, exception: (String) -> Throwable, ): T? =
                    this[key]?.let {
                        kotlin.runCatching { java.lang.Enum.valueOf(T::class.java, it) }.getOrNull()
                            ?: throw exception("Slot ${'$'}{T::class.simpleName} does not have element ${'$'}it given for variable ${'$'}key")
                    }
                
                private inline fun <reified T: Enum<T>> Map<String, String>.getRequiredSlot(key: String, exception: (String) -> Throwable, ): T =
                    getSlot<T>(key, exception) ?: throw exception("Variable ${'$'}key is required by all expressions, but is not present")
            """.trimIndent())
        }
    }

    private fun readRhinoContext(yaml: Reader): RhinoContext {
        val parsedYaml: Map<String, *> = createSnakeYamlReader().load(yaml) ?: throw IllegalYamlFile("Illegal yaml syntax")
        val contextNode = YamlNode(parsedYaml).nextMap("context") { "yaml MUST have root node: 'context:'" }
        val slots = contextNode.nextMapOrNull("slots")
            ?.asStringMultiMapEntries()
            ?.map { (name, elements) -> Slot(escapeKotlinKeywords(name), elements.map(::escapeKotlinKeywords)) }
            ?.toList() ?: emptyList()
        val intents =
            contextNode.nextMap("expressions") { "you MUST define at least one intent in 'expressions:'" }
                .asStringMultiMapEntries()
                .map { (name, expressions) ->
                    val allVariablesPerExpression = expressions.map { expression ->
                        findAllSlotsIn(expression).mapValues { (variableName, slotType) ->
                            slots.firstOrNull { it.name == slotType }
                                ?: throw IllegalYamlFile("expression: '$expression' has variable $variableName with slot type $slotType that does not exist")
                        }
                    }
                    val allRequiredVariableNames = allVariablesPerExpression
                        .map { it.keys }
                        .reduce { acc, allVariableNames -> acc.intersect(allVariableNames) }
                    val allVariables = allVariablesPerExpression.reduce { acc, it -> acc + it }
                    Intent(
                        name,
                        allVariables.map { (variableName, slotType) ->
                            SlotVariable(
                                name = escapeKotlinKeywords(variableName),
                                slotType = slotType,
                                isRequired = variableName in allRequiredVariableNames,
                            )
                        },
                    )
                }
        return RhinoContext(intents.toList(), slots)
    }

    private fun createSnakeYamlReader() = Yaml(Constructor(),
        Representer(),
        DumperOptions(),
        LoaderOptions(),
        object : Resolver() {
            override fun addImplicitResolver(tag: Tag?, regexp: Pattern?, first: String?) {
                if (tag == Tag.BOOL)
                    super.addImplicitResolver(tag, Pattern.compile(""), "")
                else
                    super.addImplicitResolver(tag, regexp, first)
            }
        },
    )

}

private val keyWords =
    "as break class continue do else false for fun if in interface is null object package return super this throw true try typealias typeof val var when while"
        .split(' ').toSet()

data class Slot(val name: String, val elements: List<String>)
data class SlotVariable(val name: String, val slotType: Slot, val isRequired: Boolean)
data class Intent(val name: String, val variables: List<SlotVariable>)
data class RhinoContext(val intents: List<Intent>, val slots: List<Slot>)

private val slotInExpression = "\\\$[A-z\\d]+:[A-z\\d]+".toRegex()
private fun findAllSlotsIn(expression: String): Map<String, String> = buildMap {
    slotInExpression.findAll(expression)
        .map { it.value.split(":") }
        .forEach { (slot, variableName) ->
            val safeVariableName = escapeKotlinKeywords(variableName)
            if (safeVariableName in this)
                throw RhinoKCodeGenerator.IllegalYamlFile("expression $expression contains duplicate variable $variableName")
            this[safeVariableName] = slot.drop(1)
        }
}

private fun escapeKotlinKeywords(variableName: String) =
    variableName.takeUnless { it in keyWords } ?: variableName.replaceFirstChar { it.uppercase() }