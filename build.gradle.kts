plugins {
	id("net.fabricmc.fabric-loom")
	`maven-publish`
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

repositories {
	maven {
		name = "Modrinth"
		url = uri("https://api.modrinth.com/maven")
	}
	maven {
		name = "Terraformers"
		url = uri("https://maven.terraformersmc.com/releases/")
	}
	maven {
		name = "Xander Maven"
		url = uri("https://maven.isxander.dev/releases")
	}
}

loom {
	splitEnvironmentSourceSets()

	mods {
		register("integrated-paper") {
			sourceSet(sourceSets.getByName("client"))
		}
	}
}

val modMenuApiStub = sourceSets.create("modMenuApiStub") {
	java.srcDir("src/modMenuApiStub/java")
	compileClasspath += loom.namedMinecraftJars
}

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")

	implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

	// Fabric API. This is technically optional, but you probably want it anyway.
	implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")

	compileOnly("dev.isxander:yet-another-config-lib:${providers.gradleProperty("yacl_version").get()}")
	add("clientCompileOnly", modMenuApiStub.output)

	runtimeOnly("dev.isxander:yet-another-config-lib:${providers.gradleProperty("yacl_version").get()}")
	runtimeOnly("maven.modrinth:viafabricplus:4.5.4")
}

tasks.withType<ProcessResources>().configureEach {
	val version = version
	inputs.property("version", version)

	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
}

java {
	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
	val projectName = project.name
	inputs.property("projectName", projectName)

	from("LICENSE") {
		rename { "${it}_$projectName" }
	}
}

// configure the maven publication
publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}
