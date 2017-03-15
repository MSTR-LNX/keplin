@file:Suppress("unused")

// could be used externally in javax.script.ScriptEngineFactory META-INF file

package uy.kohesive.keplin.kotlin.script.jsr223

import org.jetbrains.kotlin.cli.common.repl.KotlinJsr223JvmScriptEngineFactoryBase
import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.utils.PathUtil.*
import uy.kohesive.keplin.kotlin.script.util.classpathFromClass
import uy.kohesive.keplin.kotlin.script.util.classpathFromClassloader
import uy.kohesive.keplin.kotlin.script.util.classpathFromClasspathProperty
import uy.kohesive.keplin.kotlin.script.util.manifestClassPath
import java.io.File
import java.io.FileNotFoundException
import javax.script.ScriptContext
import javax.script.ScriptEngine
import kotlin.script.templates.standard.ScriptTemplateWithArgs

class BasicKotlinJsr223LocalScriptEngineFactory : KotlinJsr223JvmScriptEngineFactoryBase() {

    override fun getScriptEngine(): ScriptEngine =
            BasicKotlinJsr223LocalScriptEngine(
                    Disposer.newDisposable(),
                    this,
                    scriptCompilationClasspathFromContext(),
                    "kotlin.script.templates.standard.ScriptTemplateWithBindings",
                    { ctx, types -> ScriptArgsWithTypes(arrayOf(ctx.getBindings(ScriptContext.ENGINE_SCOPE)), types ?: emptyArray()) },
                    arrayOf(Map::class)
            )
}

class BasicKotlinJsr223DaemonCompileScriptEngineFactory : KotlinJsr223JvmScriptEngineFactoryBase() {

    override fun getScriptEngine(): ScriptEngine =
            BasicKotlinJsr223DaemonCompileScriptEngine(
                    Disposer.newDisposable(),
                    this,
                    kotlinCompilerJar,
                    scriptCompilationClasspathFromContext(),
                    "kotlin.script.templates.standard.ScriptTemplateWithBindings",
                    { ctx, types -> ScriptArgsWithTypes(arrayOf(ctx.getBindings(ScriptContext.ENGINE_SCOPE)), types ?: emptyArray()) },
                    arrayOf(Map::class)
            )
}


private fun File.existsOrNull(): File? = existsAndCheckOrNull { true }
private inline fun File.existsAndCheckOrNull(check: (File.() -> Boolean)): File? = if (exists() && check()) this else null

private fun <T> Iterable<T>.anyOrNull(predicate: (T) -> Boolean) = if (any(predicate)) this else null

private fun File.matchMaybeVersionedFile(baseName: String) =
        name == baseName ||
                name == baseName.removeSuffix(".jar") || // for classes dirs
                name.startsWith(baseName.removeSuffix(".jar") + "-")

private fun contextClasspath(keyName: String, classLoader: ClassLoader): List<File>? =
        (classpathFromClassloader(classLoader)?.anyOrNull { it.matchMaybeVersionedFile(keyName) }
                ?: manifestClassPath(classLoader)?.anyOrNull { it.matchMaybeVersionedFile(keyName) }
                )?.toList()


private fun scriptCompilationClasspathFromContext(classLoader: ClassLoader = Thread.currentThread().contextClassLoader): List<File> =
        (System.getProperty("kotlin.script.classpath")?.split(File.pathSeparator)?.map(::File)
                ?: contextClasspath(KOTLIN_JAVA_RUNTIME_JAR, classLoader)
                ?: listOf(kotlinRuntimeJar, kotlinScriptRuntimeJar)
                )
                .map { it?.canonicalFile }
                .distinct()
                .mapNotNull { it?.existsOrNull() }

private val kotlinCompilerJar: File by lazy {
    // highest prio - explicit property
    System.getProperty("kotlin.compiler.jar")?.let(::File)?.existsOrNull()
            // search classpath from context classloader and `java.class.path` property
            ?: (classpathFromClass(Thread.currentThread().contextClassLoader, K2JVMCompiler::class)
            ?: contextClasspath(KOTLIN_COMPILER_JAR, Thread.currentThread().contextClassLoader)
            ?: classpathFromClasspathProperty()
            )?.firstOrNull { it.matchMaybeVersionedFile(KOTLIN_COMPILER_JAR) }
            ?: throw FileNotFoundException("Cannot find kotlin compiler jar, set kotlin.compiler.jar property to proper location")
}

private val kotlinRuntimeJar: File? by lazy {
    System.getProperty("kotlin.java.runtime.jar")?.let(::File)?.existsOrNull()
            ?: kotlinCompilerJar.let { File(it.parentFile, KOTLIN_JAVA_RUNTIME_JAR) }.existsOrNull()
            ?: getResourcePathForClass(JvmStatic::class.java).existsOrNull()
}

private val kotlinScriptRuntimeJar: File? by lazy {
    System.getProperty("kotlin.script.runtime.jar")?.let(::File)?.existsOrNull()
            ?: kotlinCompilerJar.let { File(it.parentFile, KOTLIN_JAVA_SCRIPT_RUNTIME_JAR) }.existsOrNull()
            ?: getResourcePathForClass(ScriptTemplateWithArgs::class.java).existsOrNull()
}



