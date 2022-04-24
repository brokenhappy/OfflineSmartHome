package offlinesmarthome.app

import offlinesmarthome.rhinok.RhinoKCodeGenerator
import kotlin.io.path.createFile
import kotlin.io.path.exists

class GenerateRhinoCodeTask(private val environment: Environment) /*: DefaultTask()*/ {
//    @TaskAction
    fun generateCodeFromRhinoContext() {
        environment.kontextFile
            .also { it.toPath().takeUnless { it.exists() }?.createFile() }
            .writer().use { writer ->
                writer.appendLine("/** This is a generated file run $javaClass#generateCodeFromRhinoContext() to update it */")
                writer.appendLine("package " + GenerateRhinoCodeTask::class.java.packageName)
                writer.appendLine()
                writer.append(RhinoKCodeGenerator().fromRhinoYamlExport(environment.yamlContextFile.reader()))
            }
    }
}