import java.security.MessageDigest

plugins {
    java
    application
}

group = "com.javelin"
version = "1.1.0-beta"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.release.set(21)  //generate Java 21 compatible bytecode
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.processResources {
    filesMatching("version.properties") {
        expand("projectVersion" to project.version)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // CLI Framework
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")

    // JaCoCo Core
    implementation("org.jacoco:org.jacoco.core:0.8.12")
    implementation("org.jacoco:org.jacoco.report:0.8.12")

    // JaCoCo Agent JAR (required for -javaagent attachment)
    runtimeOnly("org.jacoco:org.jacoco.agent:0.8.12:runtime")

    // JUnit 5 Platform
    implementation("org.junit.platform:junit-platform-launcher:1.10.3")
    implementation("org.junit.platform:junit-platform-engine:1.10.3")
    implementation("org.junit.platform:junit-platform-console:1.10.3")

    // JUnit Vintage Engine
    implementation("org.junit.vintage:junit-vintage-engine:5.10.3")

    // JUnit Jupiter (JUnit 5 Support)
    implementation("org.junit.jupiter:junit-jupiter-engine:5.10.3")

    // PITest Mutation Testing
    implementation("org.pitest:pitest:1.17.4")
    implementation("org.pitest:pitest-entry:1.17.4")
    implementation("org.pitest:pitest-command-line:1.17.4")
    implementation("org.pitest:pitest-junit5-plugin:1.2.1")

    // Testing dependencies for javelin-core
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.javelin.core.Main")
    applicationName = "javelin"
    applicationDefaultJvmArgs = listOf(
        "-Xmx512m"
    )
}

val jacocoAgentJar by configurations.creating {
    isTransitive = false
}

dependencies {
    jacocoAgentJar("org.jacoco:org.jacoco.agent:0.8.12:runtime")
}

val extractJacocoAgent by tasks.registering(Copy::class) {
    from(jacocoAgentJar)
    into(layout.buildDirectory.dir("jacoco-agent"))
    rename { "jacocoagent.jar" }
}

tasks.test {
    useJUnitPlatform()
    //allows the listener to access the agent via RT.getAgent()
    dependsOn(extractJacocoAgent)
    
    val agentJar = layout.buildDirectory.file("jacoco-agent/jacocoagent.jar")
    val execFile = layout.buildDirectory.file("jacoco/test.exec")
    
    doFirst {
        jvmArgs(
            "-javaagent:${agentJar.get().asFile.absolutePath}=" +
                "destfile=${execFile.get().asFile.absolutePath}," +
                "includes=*," +
                "excludes=org.junit.*:org.jacoco.*"
        )
    }
    systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.javelin.core.Main",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
}

// Merge META-INF/services files from all dependencies
val mergeServiceFiles by tasks.registering {
    dependsOn(configurations.runtimeClasspath)
    val mergedDir = layout.buildDirectory.dir("merged-services")
    outputs.dir(mergedDir)
    
    doLast {
        val outDir = mergedDir.get().asFile.resolve("META-INF/services")
        outDir.mkdirs()
        
        val serviceEntries = mutableMapOf<String, MutableSet<String>>()
        
        // Collect from all dependency JARs
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .forEach { jar ->
                zipTree(jar).matching { include("META-INF/services/**") }.forEach { file ->
                    val lines = file.readLines().filter { it.isNotBlank() }
                    serviceEntries.getOrPut(file.name) { mutableSetOf() }.addAll(lines)
                }
            }
        
        // Write merged files
        for ((name, lines) in serviceEntries) {
            outDir.resolve(name).writeText(lines.joinToString("\n") + "\n")
        }
    }
}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("javelin-core")
    archiveVersion.set("")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    dependsOn(configurations.runtimeClasspath, mergeServiceFiles)

    from(sourceSets.main.get().output)
    
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    }) {
        exclude("META-INF/services/**")
    }
    
    // Include the merged service files
    from(mergeServiceFiles.map { layout.buildDirectory.dir("merged-services").get() })
    
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.contains("org.jacoco.agent") && it.name.contains("-runtime") }
            .map { it }
    }) {
        rename { "jacocoagent.jar" }
    }
    
    manifest {
        attributes(
            "Main-Class" to "com.javelin.core.Main"
        )
    }
}

// ── Package Manager Distribution Tasks ──────────────────────────────────────

// Ensure distZip/distTar use a clean version (strip -SNAPSHOT for releases)
val releaseVersion = version.toString().removeSuffix("-SNAPSHOT")

tasks.distZip {
    archiveFileName.set("javelin-cli-${releaseVersion}.zip")
}

tasks.distTar {
    archiveFileName.set("javelin-cli-${releaseVersion}.tar")
}

// Task: update Scoop manifest version and SHA256
tasks.register("updateScoop") {
    group = "distribution"
    description = "Updates the Scoop manifest with the current version and SHA256"
    dependsOn(tasks.distZip)

    doLast {
        val zipFile = tasks.distZip.get().archiveFile.get().asFile
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(zipFile.readBytes())
        val sha = bytes.joinToString("") { byte -> "%02x".format(byte) }

        val manifest = file("../bucket/javelin-cli.json")
        var content = manifest.readText()
        content = content.replace(Regex("\"version\":\\s*\"[^\"]+\""), "\"version\": \"${releaseVersion}\"")
        content = content.replace(Regex("\"hash\":\\s*\"[^\"]+\""), "\"hash\": \"${sha}\"")
        // Keep URL and autoupdate in sync with version
        content = content.replace(
            Regex("\"url\":\\s*\"[^\"]+\""),
            "\"url\": \"https://github.com/DesmondQue/javelin-cli/releases/download/v${releaseVersion}/javelin-cli-${releaseVersion}.zip\""
        )
        manifest.writeText(content)

        println("Updated bucket/javelin-cli.json → version=${releaseVersion}, hash=${sha}")
    }
}

// Task: update Homebrew formula version and SHA
tasks.register("updateHomebrew") {
    group = "distribution"
    description = "Updates the Homebrew formula with the current version and SHA256"
    dependsOn(tasks.distTar)

    doLast {
        val tarFile = tasks.distTar.get().archiveFile.get().asFile
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(tarFile.readBytes())
        val sha = bytes.joinToString("") { byte -> "%02x".format(byte) }

        val formula = file("../Formula/javelin-cli.rb")
        var content = formula.readText()
        content = content.replace(Regex("version \"[^\"]+\""), "version \"${releaseVersion}\"")
        content = content.replace(Regex("sha256 \"[^\"]+\""), "sha256 \"${sha}\"")
        content = content.replace(
            Regex("url \"[^\"]+\""),
            "url \"https://github.com/DesmondQue/javelin-cli/releases/download/v${releaseVersion}/javelin-cli-${releaseVersion}.tar\""
        )
        formula.writeText(content)

        println("Updated Formula/javelin-cli.rb → version=${releaseVersion}, sha256=${sha}")
    }
}
