import groovy.json.JsonSlurper
import net.fabricmc.loom.build.nesting.IncludedJarFactory
import net.fabricmc.loom.build.nesting.JarNester
import net.fabricmc.loom.task.RemapJarTask
import net.fabricmc.loom.util.Constants
import org.apache.commons.codec.digest.DigestUtils
import org.eclipse.jgit.api.Git
import java.net.URI
import java.util.*

plugins {
    java
    `maven-publish`
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

ext["getSubprojectVersion"] = object : groovy.lang.Closure<String>(this) {
    fun doCall(project: Project) : String {
        return getSubprojectVersion(project)
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
        exclude("fabric.mod.json")

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

apply(plugin = "ffapi.neo-setup")

subprojects {
    group = "org.sinytra.forgified-fabric-api"
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
        apply(plugin = "ffapi.neo-compat")
    }

    allprojects.forEach { p ->
        if (!META_PROJECTS.contains(project.name)) {
            loom.mods.register(p.name) {
                sourceSet(p.sourceSets.main.get())
            }
    
            if (p.file("src/testmod").exists() || p.file("src/testmodClient").exists()) {
                loom.mods.register(p.name + "-testmod") {
                    sourceSet(p.sourceSets.getByName("testmod"))
                }
            }
        }
    }

    var sourcesJar = tasks.named<Jar>("sourcesJar") {
        exclude("fabric.mod.json")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    tasks.withType<ProcessResources> {
        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand(Pair("version", project.version))
        }
    }

    publishing {
        publications {
            register<MavenPublication>("mavenJava") {
                pom {
                    addPomMetadataInformation(project, pom)
                }

                var jar = tasks.named<Jar>("jar");

                artifact(jar) {
                    builtBy(jar)
                }

                artifact(sourcesJar) {
                    builtBy(sourcesJar)
                }
            }
        }

        repositories {
            maven {
                val ENV = System.getenv();

                url = URI.create(ENV["MAVEN_URL"] ?: "")
                credentials {
                    username = ENV["MAVEN_USER"]
                    password = ENV["MAVEN_PASSWORD"]
                }
            }
        }
    }
}

dependencies {
	afterEvaluate {
		subprojects.forEach { proj ->
			if (proj.name in META_PROJECTS) {
				return@forEach
			}

			api(project(proj.path, "namedElements"))
			"testmodImplementation"(proj.sourceSets.getByName("testmod").output)
		}
	}
}

//publishing {
//    publications {
//        named<MavenPublication>("mavenJava") {
//            pom.withXml {
//                val depsNode = asNode().appendNode("dependencies")
//                subprojects.forEach {
//                    // The maven BOM containing all of the deprecated modules is added manually below.
//                    if (it.path.startsWith(":deprecated") || META_PROJECTS.contains(it.name)) {
//                        return@forEach
//                    }
//
//                    val depNode = depsNode.appendNode("dependency")
//                    depNode.appendNode("groupId", it.group)
//                    depNode.appendNode("artifactId", it.name)
//                    depNode.appendNode("version", it.version)
//                    depNode.appendNode("scope", "compile")
//                }
//
//                // Depend on the deprecated BOM to allow opting out of deprecated modules.
//                val depNode = depsNode.appendNode("dependency")
//                depNode.appendNode("groupId", group)
//                depNode.appendNode("artifactId", "fabric-api-deprecated")
//                depNode.appendNode("version", version)
//                depNode.appendNode("scope", "compile")
//            }
//        }
//    }
//}

val git: Git? = runCatching { Git.open(rootDir) }.getOrNull()

fun getSubprojectVersion(project: Project): String {
    // Get the version from the gradle.properties file
    val version = properties["${project.name}-version"] as? String
        ?: throw NullPointerException("Could not find version for " + project.name)

    if (git == null) {
        return "$version+nogit"
    }

    val latestCommits = git.log().addPath(project.name).setMaxCount(1).call().toList()
    if (latestCommits.isEmpty()) {
        return "$version+uncommited"
    }

    return version + "+" + latestCommits[0].id.name.substring(0, 8) + DigestUtils.sha256Hex(versionMc).substring(0, 2)
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

fun addPomMetadataInformation(project: Project, pom: MavenPom) {
    var modJsonFile = project.file("src/main/resources/fabric.mod.json")

    if (!modJsonFile.exists()) {
        modJsonFile = project.file("src/client/resources/fabric.mod.json")
    }

    val modJson = JsonSlurper().parse(modJsonFile) as Map<*, *>
    pom.name = modJson["name"] as String
    pom.url = "https://github.com/FabricMC/fabric/tree/HEAD/${project.rootDir.relativeTo(project.projectDir)}"
    pom.description = modJson["description"] as String
    pom.licenses {
        license {
            name = "Apache-2.0"
            url = "https://github.com/FabricMC/fabric/blob/HEAD/LICENSE"
        }
    }
    pom.developers {
        developer {
            name = "FabricMC"
            url = "https://fabricmc.net/"
        }
    }
    pom.scm {
        connection = "scm:git:https://github.com/FabricMC/fabric.git"
        url = "https://github.com/FabricMC/fabric"
        developerConnection = "scm:git:git@github.com:FabricMC/fabric.git"
    }
    pom.issueManagement {
        system = "GitHub"
        url = "https://github.com/FabricMC/fabric/issues"
    }
}