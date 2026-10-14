import type { Category, Severity, ToolName, VulnStatus } from './types'
import { SEVERITIES } from './types'

/** Poids alignés avec backend-go/internal/service/score.go et desktop/ScoreCalculator.kt */
export const SEVERITY_WEIGHTS: Record<Severity, number> = {
  CRITICAL: 10,
  HIGH: 5,
  MEDIUM: 2,
  LOW: 0.5,
}

export const SEVERITY_ORDER: Record<Severity, number> = {
  CRITICAL: 0,
  HIGH: 1,
  MEDIUM: 2,
  LOW: 3,
}

/**
 * Couleurs de sévérité, validées (bande de luminance, séparation daltonisme)
 * sur les surfaces sombres #141D2E et #0F0F12. Toujours accompagnées du libellé.
 */
export const SEVERITY_META: Record<Severity, { label: string; color: string }> = {
  CRITICAL: { label: 'Critique', color: '#D33A80' },
  HIGH: { label: 'Haute', color: '#BF5410' },
  MEDIUM: { label: 'Moyenne', color: '#A0991F' },
  LOW: { label: 'Basse', color: '#3A80E0' },
}

export const STATUS_META: Record<VulnStatus, { label: string; tone: 'open' | 'progress' | 'pending' | 'done' | 'muted' }> = {
  OPEN: { label: 'Ouverte', tone: 'open' },
  REOPENED: { label: 'Réouverte', tone: 'open' },
  IN_PROGRESS: { label: 'En cours', tone: 'progress' },
  FIXED_PENDING_VERIFY: { label: 'Corrigée, à vérifier', tone: 'pending' },
  RESOLVED: { label: 'Résolue', tone: 'done' },
  IGNORED: { label: 'Ignorée', tone: 'muted' },
}

export const TOOL_LABEL: Record<ToolName, string> = {
  // Moteur SAST au format Semgrep, exécuté par Opengrep depuis la migration.
  SEMGREP: 'Opengrep',
  GITLEAKS: 'Gitleaks',
  TRIVY: 'Trivy',
  BANDIT: 'Bandit', // historique (moteur retiré)
  CHECKOV: 'Checkov', // historique (moteur retiré)
  OSV_SCANNER: 'OSV-Scanner',
  DEPENDENCY_CHECK: 'Dependency-Check',
}

export const CATEGORY_LABEL: Record<Category, string> = {
  SAST: 'Code',
  SECRET: 'Secret',
  SCA: 'Dépendance',
  IAC: 'Infra',
}

export function isSeverity(value: unknown): value is Severity {
  return typeof value === 'string' && (SEVERITIES as readonly string[]).includes(value)
}

export function compareSeverity(a: Severity, b: Severity): number {
  return SEVERITY_ORDER[a] - SEVERITY_ORDER[b]
}
