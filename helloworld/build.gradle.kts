import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import org.jetbrains.kotlin.gradle.tasks.KotlinJsDce

buildscript {
    extra.set("production", (findProperty("prod") ?: findProperty("production") ?: "false") == "true")
}

plugins {
    val kotlinVersion: String by System.getProperties()
    id("kotlinx-serialization") version kotlinVersion
    kotlin("js") version kotlinVersion
}

version = "1.0.0-SNAPSHOT"
group = "com.example"

repositories {
    mavenCentral()
    jcenter()
    maven { url = uri("https://dl.bintray.com/kotlin/kotlin-eap") }
    maven { url = uri("https://kotlin.bintray.com/kotlinx") }
    maven { url = uri("https://dl.bintray.com/kotlin/kotlin-js-wrappers") }
    maven {
        url = uri("https://dl.bintray.com/gbaldeck/kotlin")
        metadataSources {
            mavenPom()
            artifact()
        }
    }
    maven { url = uri("https://dl.bintray.com/rjaros/kotlin") }
    mavenLocal()
}

// Versions
val kotlinVersion: String by System.getProperties()
val kvisionVersion: String by System.getProperties()

// Custom Properties
val webDir = file("src/main/web")
val isProductionBuild = project.extra.get("production") as Boolean

kotlin {
    target {
        compilations.all {
            kotlinOptions {
                moduleKind = "umd"
                sourceMap = !isProductionBuild
                if (!isProductionBuild) {
                    sourceMapEmbedSources = "always"
                }
            }
        }
        browser {
            runTask {
                outputFileName = "main.bundle.js"
                devServer = KotlinWebpackConfig.DevServer(
                    open = false,
                    port = 3000,
                    proxy = mapOf(
                        "/kv/*" to "http://localhost:8080",
                        "/kvws/*" to mapOf("target" to "ws://localhost:8080", "ws" to true)
                    ),
                    contentBase = listOf("$buildDir/processedResources/Js/main")
                )
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }
    sourceSets["main"].dependencies {
        implementation(kotlin("stdlib-js"))
        implementation(npm("po2json"))
        implementation(npm("grunt"))
        implementation(npm("grunt-pot"))

        implementation("pl.treksoft:kvision:$kvisionVersion")
        implementation("pl.treksoft:kvision-i18n:$kvisionVersion")
    }
    sourceSets["test"].dependencies {
        implementation(kotlin("test-js"))
        implementation("pl.treksoft:kvision-testutils:$kvisionVersion:tests")
    }
    sourceSets["main"].resources.srcDir(webDir)
}

tasks {
    withType<KotlinJsDce> {
        doLast {
            copy {
                file("$buildDir/tmp/expandedArchives/").listFiles()?.forEach {
                    if (it.isDirectory && it.name.startsWith("kvision")) {
                        from(it) {
                            include("css/**")
                            include("img/**")
                            include("js/**")
                        }
                    }
                }
                into(file("${buildDir.path}/js/packages/${project.name}/kotlin-dce"))
            }
        }
    }
    create("generateGruntfile") {
        outputs.file("$buildDir/js/Gruntfile.js")
        doLast {
            file("$buildDir/js/Gruntfile.js").run {
                writeText(
                    """
                    module.exports = function (grunt) {
                        grunt.initConfig({
                            pot: {
                                options: {
                                    text_domain: "messages",
                                    dest: "../../src/main/resources/i18n/",
                                    keywords: ["tr", "ntr:1,2", "gettext", "ngettext:1,2"],
                                    encoding: "UTF-8"
                                },
                                files: {
                                    src: ["../../src/main/kotlin/**/*.kt"],
                                    expand: true,
                                },
                            }
                        });
                        grunt.loadNpmTasks("grunt-pot");
                    };
                """.trimIndent()
                )
            }
        }
    }
    create("generatePotFile", Exec::class) {
        dependsOn("kotlinNpmInstall", "generateGruntfile")
        workingDir = file("$buildDir/js")
        executable = NodeJsRootPlugin.apply(project).nodeCommand
        args("$buildDir/js/node_modules/grunt/bin/grunt", "pot")
        inputs.files(kotlin.sourceSets["main"].kotlin.files)
        outputs.file("$projectDir/src/main/resources/i18n/messages.pot")
    }
}
afterEvaluate {
    tasks {
        getByName("processResources", Copy::class) {
            dependsOn("kotlinNpmInstall")
            exclude("**/*.pot")
            doLast("Convert PO to JSON") {
                destinationDir.walkTopDown().filter {
                    it.isFile && it.extension == "po"
                }.forEach {
                    exec {
                        executable = NodeJsRootPlugin.apply(project).nodeCommand
                        args(
                            "$buildDir/js/node_modules/po2json/bin/po2json",
                            it.absolutePath,
                            "${it.parent}/${it.nameWithoutExtension}.json",
                            "-f",
                            "jed1.x"
                        )
                        println("Converted ${it.name} to ${it.nameWithoutExtension}.json")
                    }
                    it.delete()
                }
                copy {
                    file("$buildDir/tmp/expandedArchives/").listFiles()?.forEach {
                        if (it.isDirectory && it.name.startsWith("kvision")) {
                            val kvmodule = it.name.split("-$kvisionVersion").first()
                            from(it) {
                                include("css/**")
                                include("img/**")
                                include("js/**")
                                if (kvmodule == "kvision") {
                                    into("kvision/$kvisionVersion")
                                } else {
                                    into("kvision-$kvmodule/$kvisionVersion")
                                }
                            }
                        }
                    }
                    into(file(buildDir.path + "/js/packages_imported"))
                }
            }
        }
        create("zip", Zip::class) {
            dependsOn("browserProductionWebpack")
            group = "package"
            destinationDirectory.set(file("$buildDir/libs"))
            val distribution =
                project.tasks.getByName("browserProductionWebpack", KotlinWebpack::class).destinationDirectory!!
            from(distribution) {
                include("*.*")
            }
            from(webDir)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            inputs.files(distribution, webDir)
            outputs.file(archiveFile)
        }
    }
}
