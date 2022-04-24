package offlinesmarthome.rhinok

import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RhinoKCodeGenerationTest {

    private fun assertRhinoYamlGeneratesClasses(@Language("kt") code: String, @Language("yaml") yaml: String) {
        assertEquals(code, withoutFunctions(RhinoKCodeGenerator().fromRhinoYamlExport(yaml.reader()).lines()))
    }

    private fun withoutFunctions(actual: List<String>) = (
            actual.dropWhile { it.startsWith("import") }.drop(1)
                .takeWhile { it != "    companion object {" }
            + actual.dropWhile { it != "    companion object {" }
                .dropWhile { it != "}" }
                .takeWhile { !it.startsWith("private inline fun") }
            ).joinToString("\n") + "\n"

    private fun assertIllegalYaml(@Language("yaml") yaml: String) {
        assertThrows<RhinoKCodeGenerator.IllegalYamlFile> {
            RhinoKCodeGenerator().fromRhinoYamlExport(yaml.reader())
        }
    }

    @Test
    fun `empty yaml files are not allowed`() {
        assertIllegalYaml("")
    }

    @Test
    fun `context must contain at least one expression`() {
        assertIllegalYaml("context:")
    }

    @Test
    fun `expression with slots must refer to an existing slot`() {
        assertIllegalYaml(
            """
                context:
                  expressions:
                    Foo:
                      - let's ${'$'}slotThatDoesNotExist:bla
            """.trimIndent(),
        )
    }

    @Test
    fun `expression slot names must be unique`() {
        assertIllegalYaml(
            """
                context:
                  expressions:
                    Foo:
                      - let's ${'$'}Slot:bla ${'$'}OtherType:bla
                  slots:
                    Slot:
                      - foo
                      - bar
                    OtherType:
                      - foo
                      - bar
            """.trimIndent(),
        )
    }

    @Test
    fun `expression without slots creates object class`() {
        assertRhinoYamlGeneratesClasses(
            """
                sealed interface Intent {
                    object NotUnderstood : Intent
                    object Foo : Intent
                }

            """.trimIndent(),
            """
                context:
                  expressions:
                    Foo:
                      - bla
            """.trimIndent(),
        )
    }

    @Test
    fun `if slot is used multiple times, it is still only created once`() {
        assertRhinoYamlGeneratesClasses(
            """
                sealed interface Intent {
                    object NotUnderstood : Intent
                    data class Foo(val bla: Slot, val bloo: Slot) : Intent
                    data class Bar(val bla: Slot) : Intent
                }
                enum class Slot {
                    One,
                    Two,
                }

            """.trimIndent(),
            """
                context:
                  expressions:
                    Foo:
                      - do ${'$'}Slot:bla ${'$'}Slot:bloo
                    Bar:
                      - do that ${'$'}Slot:bla
                  slots:
                    Slot:
                      - One
                      - Two
            """.trimIndent(),
        )
    }

    @Test
    fun `if variable doesn't occur in all expressions of an intent, it is optional`() {
        assertRhinoYamlGeneratesClasses(
            """
                sealed interface Intent {
                    object NotUnderstood : Intent
                    data class Foo(val bla: Slot?) : Intent
                }
                enum class Slot {
                    One,
                    Two,
                }

            """.trimIndent(),
            """
                context:
                  expressions:
                    Foo:
                      - do ${'$'}Slot:bla
                      - do all
                  slots:
                    Slot:
                      - One
                      - Two
            """.trimIndent(),
        )
    }

    @Test
    fun `if variable is Kotlin keyword, it gets capitalized`() {
        assertRhinoYamlGeneratesClasses(
            """
                sealed interface Intent {
                    object NotUnderstood : Intent
                    data class Foo(val True: Slot) : Intent
                }
                enum class Slot {
                    One,
                    Two,
                }

            """.trimIndent(),
            """
                context:
                  expressions:
                    Foo:
                      - do ${'$'}Slot:true
                  slots:
                    Slot:
                      - One
                      - Two
            """.trimIndent(),
        )
    }

    @Test
    fun `if slot element is Kotlin keyword, it gets capitalized`() {
        assertRhinoYamlGeneratesClasses(
            """
                sealed interface Intent {
                    object NotUnderstood : Intent
                    data class Foo(val bla: Slot) : Intent
                }
                enum class Slot {
                    True,
                    Two,
                }

            """.trimIndent(),
            """
                context:
                  expressions:
                    Foo:
                      - do ${'$'}Slot:bla
                  slots:
                    Slot:
                      - true
                      - Two
            """.trimIndent(),
        )
    }
}