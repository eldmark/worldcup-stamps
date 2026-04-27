import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.estampitas.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/kotlin"))
            dependencies {
                implementation(platform("io.github.jan-tennert.supabase:bom:${libs.versions.supabase.get()}"))
                implementation(libs.supabase.postgrest)
                implementation(libs.supabase.auth)
                implementation(libs.ktor.client.core)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.lifecycle.viewmodel)
                implementation(libs.uuid)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.ktor.client.android)
            }
        }
    }
}

val generateSupabaseConfig by tasks.registering {
    val localFile = rootProject.file("local.properties")
    val outDir = layout.buildDirectory.dir("generated/kotlin/com/estampitas/config")
    outputs.dir(outDir)
    inputs.file(localFile).optional()

    doLast {
        val props = Properties()
        if (localFile.exists()) {
            localFile.inputStream().use { props.load(it) }
        }
        val url = props.getProperty("supabase.url", "https://YOUR_PROJECT_REF.supabase.co")
        val anon = props.getProperty("supabase.anon.key", "YOUR_SUPABASE_ANON_KEY")
        val ref = props.getProperty("supabase.project.ref", "YOUR_PROJECT_REF")
        val dir = outDir.get().asFile
        dir.mkdirs()
        dir.resolve("SupabaseConfig.kt").writeText(
            """
            package com.estampitas.config

            /** Valores generados desde la raíz [local.properties] al compilar. */
            public object SupabaseConfig {
                public const val PROJECT_REF: String = ${kotlinString(ref)}
                public const val URL: String = ${kotlinString(url)}
                public const val ANON_KEY: String = ${kotlinString(anon)}
            }
            """.trimIndent() + "\n",
        )
    }
}

fun kotlinString(value: String): String =
    buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

tasks.configureEach {
    if (name.startsWith("compile") && name.contains("Kotlin", ignoreCase = true)) {
        dependsOn(generateSupabaseConfig)
    }
}
