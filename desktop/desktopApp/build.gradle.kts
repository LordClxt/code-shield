import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)

    testImplementation(libs.kotlin.testJunit)
}

// Galerie de captures (SnapshotGallery) : rendue seulement si SNAPSHOTS_DIR est défini.
tasks.test {
    environment("SNAPSHOTS_DIR", System.getenv("SNAPSHOTS_DIR") ?: "")
}

compose.desktop {
    application {
        mainClass = "org.example.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // Nom affiché par l'OS (Applications, menu Démarrer) et nom des installeurs.
            packageName = "CodeShield"
            // MAJOR.MINOR.PATCH (exigé par le .msi) ; la CI le fixe depuis le tag (v1.2.3 -> 1.2.3).
            packageVersion = (findProperty("appVersion") as String?) ?: "1.0.0"
            vendor = "CodeShield"
        }
    }
}