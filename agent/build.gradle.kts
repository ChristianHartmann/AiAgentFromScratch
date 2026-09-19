plugins {
	java
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

group = "dev.aiengineer"
version = "0.0.1-SNAPSHOT"
description = "AI agent from scratch in Java"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

extra["springAiVersion"] = "2.0.1"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.ai:spring-ai-starter-model-openai")
	implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
	implementation("org.springframework.ai:spring-ai-starter-model-google-genai")
	implementation("org.springframework.ai:spring-ai-google-genai-embedding")
	implementation("org.springframework.ai:spring-ai-mcp")
	implementation("com.knuddels:jtokkit:1.1.0")
	implementation("org.jsoup:jsoup:1.23.2")
	implementation("org.apache.poi:poi:5.5.1")
	implementation("org.apache.poi:poi-ooxml:5.5.1")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
	}
}

tasks.test {
	useJUnitPlatform {
		excludeTags("llm", "external")
	}
}

/**
 * Tests that need the network. They are kept out of the regular build and run on demand:
 * "llm" tests call a model and cost quota, "external" tests only need Hugging Face, SearXNG
 * or an MCP server and are free.
 */
fun registerLiveTest(name: String, tag: String, taskDescription: String) = tasks.register<Test>(name) {
	description = taskDescription
	group = "verification"
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform {
		includeTags(tag)
	}
	outputs.upToDateWhen { false }
}

registerLiveTest("llmTest", "llm", "Tests that call a real language model and use up its quota")

registerLiveTest("externalTest", "external", "Tests against Hugging Face, SearXNG and MCP servers, without a model").configure {
	dependsOn(":mcp-search-server:bootJar")
	systemProperty("mcpSearchServerJar",
		rootProject.layout.projectDirectory.file("mcp-search-server/build/libs/mcp-search-server.jar").asFile.absolutePath)
}
