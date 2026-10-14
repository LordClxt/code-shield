package codeshield.logic

import codeshield.model.Category
import codeshield.model.Finding

/** sha256 hexadécimal minuscule (implémentation plateforme). */
expect fun sha256Hex(input: String): String

/**
 * Empreinte stable d'une faille, calculée LOCALEMENT avant la synchro.
 *
 * Le backend s'en sert comme identité : une faille déplacée de quelques lignes
 * reste la même issue (au lieu d'être résolue puis recréée). Seul le hash part
 * sur le réseau — jamais le texte de la ligne (directive Sécurité Intellectuelle).
 *
 * Entrée hachée : `v1|outil|règle|fichier|ancre|occurrence|sel`
 *  - ancre = ligne de code normalisée (espaces compactés) pour SAST/IaC ;
 *    `pkg:<nom>` pour la SCA (la ligne d'un manifeste n'a pas de sens) ;
 *    vide pour les SECRETS (on ne hache jamais une ligne contenant un secret) ;
 *  - occurrence = rang parmi les findings de même (outil, règle, fichier, ancre),
 *    pour distinguer deux lignes identiques ;
 *  - sel = identifiant du projet CodeShield (empêche les tables précalculées).
 *
 * Format aligné avec contracts/openapi.yaml#/components/schemas/Finding.
 */
object Fingerprint {
    fun normalize(line: String): String = line.trim().replace(Regex("\\s+"), " ")

    fun anchor(f: Finding, lines: List<String>?): String = when (f.effectiveCategory) {
        Category.SECRET -> ""
        Category.SCA -> "pkg:${f.packageName.orEmpty()}"
        else -> lines?.getOrNull(f.lineNumber - 1)?.let(::normalize).orEmpty()
    }

    /**
     * Ajoute une empreinte à chaque finding. [readLines] lit un fichier relatif
     * au projet (null si illisible) ; chaque fichier n'est lu qu'une fois.
     */
    fun apply(findings: List<Finding>, salt: String, readLines: (String) -> List<String>?): List<Finding> {
        val cache = HashMap<String, List<String>?>()
        val anchored = findings.map { f ->
            val lines = if (f.effectiveCategory == Category.SAST || f.effectiveCategory == Category.IAC) {
                cache.getOrPut(f.filePath) { runCatching { readLines(f.filePath) }.getOrNull() }
            } else null
            f to anchor(f, lines)
        }
        val occurrences = HashMap<String, Int>()
        // Rang stable : tri par position avant de numéroter les occurrences.
        val order = anchored.indices.sortedWith(compareBy({ anchored[it].first.filePath }, { anchored[it].first.lineNumber }))
        val result = arrayOfNulls<Finding>(anchored.size)
        for (i in order) {
            val (f, anchor) = anchored[i]
            val base = listOf(f.tool.name, f.ruleId, f.filePath, anchor).joinToString("|")
            val occ = occurrences.getOrElse(base) { 0 }
            occurrences[base] = occ + 1
            result[i] = f.copy(fingerprint = sha256Hex("v1|$base|$occ|$salt"))
        }
        return result.map { it!! }
    }
}
