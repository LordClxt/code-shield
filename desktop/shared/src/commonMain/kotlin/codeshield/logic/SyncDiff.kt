package codeshield.logic

import codeshield.model.Finding
import codeshield.model.FindingKey

/** Résultat du diff local (miroir de la logique backend sync_service). */
data class SyncDiffResult(
    val toCreate: List<Finding>,
    val toTouch: List<Finding>,
    val toResolve: List<FindingKey>,
)

/**
 * Diff purement local entre les clés connues (dernier rapport) et le nouveau scan.
 * - clé entrante inconnue -> toCreate
 * - clé entrante connue -> toTouch (last_seen à rafraîchir)
 * - clé connue absente du scan -> toResolve
 */
object SyncDiff {
    fun computeDiff(knownKeys: Set<FindingKey>, incoming: List<Finding>): SyncDiffResult {
        val incomingKeys = incoming.map { it.identityKey() }.toSet()
        return SyncDiffResult(
            toCreate = incoming.filter { it.identityKey() !in knownKeys },
            toTouch = incoming.filter { it.identityKey() in knownKeys },
            toResolve = knownKeys.filter { it !in incomingKeys },
        )
    }
}
