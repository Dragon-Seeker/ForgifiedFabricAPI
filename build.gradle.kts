import net.fabricmc.loom.build.nesting.IncludedJarFactory
import net.fabricmc.loom.build.nesting.JarNester
import net.fabricmc.loom.util.Constants
import net.fabricmc.loom.util.GroovyXmlUtil
import org.apache.commons.codec.digest.DigestUtils
import java.util.*

plugins {
    java
    `maven-publish`
    id("org.ajoberstar.grgit") version "4.1.1"
    id("dev.architectury.loom") // Version declared in buildSrc
}

val implementationVersion: String by project
val versionMc: String by project
val versionForge: String by project
val versionForgifiedFabricLoader: String by project

val META_PROJECTS: List<String> = listOf(
    "deprecated",
    "fabric-api-bom",
    "fabric-api-catalog"
)
val DEV_ONLY_MODULES: List<String> = listOf(
    "fabric-gametest-api-v1"
)

ext["getSubprojectVersion"] = object : groovy.lang.Closure<Unit>(this) {
    fun doCall(project: Project) {
        getSubprojectVersion(project)
    }
}
ext["moduleDependencies"] = object : groovy.lang.Closure<Unit>(this) {
    fun doCall(project: Project, depNames: List<String>) {
        moduleDependencies(project, depNames)
    }
}
ext["testDependencies"] = object : groovy.lang.Closure<Unit>(this) {
    fun doCall(project: Project, depNames: List<String>) {
        testDependencies(project, depNames)
    }
}

val upstreamVersion = version

ext["upstreamVersion"] = upstreamVersion

group = "org.sinytra"
version = "$upstreamVersion+$implementationVersion+$versionMc"
println("Version: $version")

allprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "dev.architectury.loom")

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        withSourcesJar()
    }

    repositories {
        mavenCentral()
        maven {
            name = "FabricMC"
            url = uri("https://maven.fabricmc.net")
        }
        maven {
            name = "Mojank"
            url = uri("https://libraries.minecraft.net/")
        }
        maven {
            name = "NeoForged"
            url = uri("https://maven.neoforged.net/releases")
        }
        maven {
            name = "Sinytra"
            url = uri("https://maven.su5ed.dev/releases")
        }
        mavenLocal()
    }

    dependencies {
        minecraft(group = "com.mojang", name = "minecraft", version = versionMc)
        neoForge(group = "net.neoforged", name = "neoforge", version = versionForge)
        mappings(loom.layered {
            officialMojangMappings {
                nameSyntheticMembers = true
            }
        })
    }

    tasks.named<Jar>("jar") {
        doLast {
            val factory = IncludedJarFactory(project)
            val nestedJars = factory.getNestedJars(configurations.getByName(Constants.Configurations.INCLUDE))

            if (!nestedJars.isPresent) {
                logger.info("No jars to nest")
                return@doLast
            }

            val jars: MutableSet<File> = LinkedHashSet(nestedJars.get().files)
            JarNester.nestJars(
                jars,
                emptyList(),
                archiveFile.get().asFile,
                loom.platform.get(),
                project.logger
            )
        }
    }
}

dependencies {
    // Include Forgified Fabric Loader
    include("org.sinytra:fabric-loader:$versionForgifiedFabricLoader:full")
}

tasks {
    withType<JavaCompile> {
        options.release = 21
    }
}

// Subprojects

allprojects {
    val modDependencies: Configuration by configurations.creating

    tasks.register("generate") {
        group = "sinytra"
    }

    // Setup must come before generators
    apply(plugin = "ffapi.neo-setup")
    apply(plugin = "ffapi.neo-conversion")
    apply(plugin = "ffapi.neo-entrypoint")
    apply(plugin = "ffapi.package-info")

    if (!META_PROJECTS.contains(name) && project != rootProject) {
//        apply(plugin = "ffapi.neo-compat") TODO
    }

    if (!META_PROJECTS.contains(project.name)) {
        loom.mods.register(project.name) {
            sourceSet(sourceSets.main.get())
        }

        if (project.file("src/testmod").exists() || project.file("src/testmodClient").exists()) {
            loom.mods.register(project.name + "-testmod") {
                sourceSet(sourceSets.getByName("testmod"))
            }
        }
    }

    tasks.named<Jar>("sourcesJar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    publishing {
        publications {
            register<MavenPublication>("mavenJava") {
                pom {
//                    addPomMetadataInformation(project, pom) TODO
                }
            }
        }
    }
}

publishing {
    publications {
        named<MavenPublication>("mavenJava") {
            from(components["java"])

            pom.withXml {
                val depsNode = GroovyXmlUtil.getOrCreateNode(asNode(), "dependencies")
                rootProject.configurations.include.get().dependencies.forEach {
                    val depNode = depsNode.appendNode("dependency")
                    depNode.appendNode("groupId", it.group)
                    depNode.appendNode("artifactId", it.name)
                    depNode.appendNode("version", it.version)
                    depNode.appendNode("scope", "compile")
                }
            }
        }
    }
}

fun getSubprojectVersion(project: Project): String {
    // Get the version from the gradle.properties file
    val version = properties["${project.name}-version"] as? String
        ?: throw NullPointerException("Could not find version for " + project.name)

    @Suppress("SENSELESS_COMPARISON")
    if (grgit == null) {
        return "$version+nogit"
    }

    val latestCommits = grgit.log(mapOf("paths" to listOf(project.name), "maxCommits" to 1))
    if (latestCommits.isEmpty()) {
        return "$version+uncommited"
    }

    return version + "+" + latestCommits[0].id.substring(0, 8) + DigestUtils.sha256Hex(versionMc).substring(0, 2)
}

fun moduleDependencies(project: Project, depNames: List<String>) {
    val deps = depNames.map { project.dependencies.project(":$it", "namedElements") }

    project.dependencies {
        deps.forEach {
            api(it)
        }
    }

    // As we manually handle the maven artifacts, we need to also manually specify the deps.
    project.publishing {
        publications {
            named<MavenPublication>("mavenJava") {
                pom.withXml {
                    val depsNode = asNode().appendNode("dependencies")
                    deps.forEach {
                        val depNode = depsNode.appendNode("dependency")
                        depNode.appendNode("groupId", it.group)
                        depNode.appendNode("artifactId", it.name)
                        depNode.appendNode("version", it.version)
                        depNode.appendNode("scope", "compile")
                    }
                }
            }
        }
    }
}

fun testDependencies(project: Project, depNames: List<String>) {
    val deps = depNames.map { project.dependencies.project(":$it", "namedElements") }

    project.dependencies {
        deps.forEach {
            "testmodImplementation"(it)
        }
    }
}