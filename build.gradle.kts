import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
	kotlin("jvm") version "1.9.10"
	id("io.ktor.plugin") version "2.3.4"
}

group = "missdee"
version = "1.0.0"

application {
	mainClass.set("missdee.ApplicationKt")

	val isDevelopment = project.ext.has("development")
	applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("io.ktor:ktor-server-core-jvm:2.2.4")
	implementation("io.ktor:ktor-server-netty-jvm:2.2.4")
	implementation("ch.qos.logback:logback-classic:$logback_version")
	implementation("com.nimbusds:nimbus-jose-jwt:9.30.2")
	implementation("org.xerial:sqlite-jdbc:3.40.1.0")
	testImplementation("io.ktor:ktor-server-tests-jvm:2.2.4")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
	implementation(kotlin("stdlib-jdk8"))
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
	jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
	jvmTarget = "1.8"
}