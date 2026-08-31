plugins {
	java
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

group = "dev.aiengineer"
version = "0.0.1-SNAPSHOT"
description = "MCP server offering a web search tool over stdio, section 3.4.4"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.ai:spring-ai-starter-mcp-server")
	implementation("org.springframework.boot:spring-boot-starter-restclient")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:2.0.1")
	}
}

tasks.bootJar {
	archiveFileName = "mcp-search-server.jar"
}

tasks.test {
	useJUnitPlatform()
}
