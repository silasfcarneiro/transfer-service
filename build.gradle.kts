plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.spring)
	alias(libs.plugins.kotlin.jpa)
	alias(libs.plugins.spring.boot)
	alias(libs.plugins.spring.dependency.management)
}

group = "com.payments"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(libs.spring.boot.starter.data.jpa)
	implementation(libs.spring.boot.starter.flyway)
	implementation(libs.spring.boot.starter.validation)
	implementation(libs.spring.boot.starter.webmvc)
	implementation(libs.spring.boot.starter.data.mongodb)
	implementation(libs.spring.kafka)
	implementation(libs.flyway.database.postgresql)
	implementation(libs.kotlin.reflect)
	implementation(libs.jackson.module.kotlin)
	runtimeOnly(libs.postgresql)

	testImplementation(libs.mockito.kotlin)
	testImplementation(libs.spring.boot.testcontainers)
	testImplementation(libs.testcontainers.junit)
	testImplementation(libs.testcontainers.postgresql)
	testImplementation(libs.testcontainers.kafka)
	testImplementation(libs.testcontainers.mongodb)
	testImplementation(libs.awaitility)

	testImplementation(libs.spring.boot.starter.test)
	testImplementation(libs.kotlin.test.junit5)
	testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}