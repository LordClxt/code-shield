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
        // Nom de l'app dans la barre de menus et le Dock macOS (sinon « MainKt » avec ./gradlew run).
        // Options propres à la JVM macOS : ajoutées seulement quand on construit sur un Mac.
        if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
            jvmArgs("-Xdock:name=CodeShield", "-Dapple.awt.application.name=CodeShield")
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // Nom affiché par l'OS (Applications, menu Démarrer) et nom des installeurs.
            packageName = "CodeShield"
            // MAJOR.MINOR.PATCH (exigé par le .msi) ; la CI le fixe depuis le tag (v1.2.3 -> 1.2.3).
            packageVersion = (findProperty("appVersion") as String?) ?: "1.0.0"
            vendor = "CodeShield"

            // Icône d'app (le d-bouclier) : générée depuis le logo officiel.
            macOS { iconFile.set(project.file("icons/CodeShield.icns")) }
            windows { iconFile.set(project.file("icons/CodeShield.ico")) }
            linux { iconFile.set(project.file("icons/CodeShield.png")) }
        }
    }
}