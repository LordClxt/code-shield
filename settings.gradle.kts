// Build composite du monorepo CodeShield.
//
// La racine n'a pas de code Gradle propre : elle inclut le build Desktop
// (Kotlin Multiplatform) afin que toute invocation Gradle depuis la racine
// (terminal, IDE) fonctionne au lieu d'échouer avec
// "settings.gradle[.kts|.dcl] is not found!".
//
// Backend (Go) et frontend (Bun) ne sont pas concernés par Gradle.
//
// Usage depuis la racine :
//   ./desktop/gradlew :desktop:shared:jvmTest   (tests unitaires Desktop)
// ou depuis desktop/ :
//   ./gradlew :shared:jvmTest
rootProject.name = "codeshield"

includeBuild("desktop")
