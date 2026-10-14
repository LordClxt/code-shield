/**
 * Types du front, dérivés du contrat unique contracts/openapi.yaml
 * (`bun run contracts` régénère api-schema.gen.ts). Aucun type ne porte de
 * code source : uniquement des métadonnées de positionnement.
 */
import type { components } from './api-schema.gen'

type S = components['schemas']

export type Severity = S['Severity']
export type ToolName = S['ToolName']
export type Category = S['Category']
export type VulnStatus = S['VulnStatus']
export type Scope = S['Scope']

// Listes ordonnées (itérables) ; `satisfies` garantit l'alignement avec le contrat.
export const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const satisfies readonly Severity[]
export const TOOLS = [
  'SEMGREP', 'GITLEAKS', 'TRIVY', 'BANDIT', 'CHECKOV', 'OSV_SCANNER', 'DEPENDENCY_CHECK',
] as const satisfies readonly ToolName[]
export const CATEGORIES = ['SAST', 'SECRET', 'SCA', 'IAC'] as const satisfies readonly Category[]
export const VULN_STATUSES = [
  'OPEN', 'IN_PROGRESS', 'FIXED_PENDING_VERIFY', 'REOPENED', 'RESOLVED', 'IGNORED',
] as const satisfies readonly VulnStatus[]
export const SCOPES = ['sync:write', 'issues:read', 'issues:write'] as const satisfies readonly Scope[]

export type Finding = S['Finding']
export type User = S['User']
export type Project = S['Project']
export type ProjectMetrics = S['ProjectMetrics']
export type TimelinePoint = S['TimelinePoint']
export type Scan = S['Scan']
export type Vulnerability = S['Vulnerability']
export type Issue = S['Issue']
export type IssuePage = S['IssuePage']
export type IssueDetail = S['IssueDetail']
export type FixContext = S['FixContext']
export type SyncResult = S['SyncSubmitResponse']
export type ApiKey = S['ApiKey']
export type ApiKeyCreated = S['ApiKeyCreated']
export type ApiKeyCreate = S['ApiKeyCreate']

/** Statuts encore présents dans le code (comptés dans le score). */
export const ACTIVE_STATUSES: readonly VulnStatus[] = ['OPEN', 'IN_PROGRESS', 'REOPENED', 'FIXED_PENDING_VERIFY']
