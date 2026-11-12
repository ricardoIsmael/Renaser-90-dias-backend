#!/usr/bin/env bash
# Funciones comunes de los scripts del WAF (spec 050 tramo B). Se carga con `source`; no se corre solo.
#
# Reglas de D12 / FR-011 que cumplen los tres scripts: simulacro por omision, perfil `prod` explicito y cuenta
# verificada (RENASER_CUENTA_AWS, nunca escrita en el repositorio), idempotentes, respaldo antes de cambiar y
# `set -euo pipefail`. El WAF de alcance CLOUDFRONT solo existe en us-east-1: por eso la region va fija.
# shellcheck shell=bash

PERFIL=prod
REGION=us-east-1
ALCANCE=CLOUDFRONT
AQUI=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
PLANTILLA="$AQUI/web-acl.plantilla.json"
RESPALDOS=${RENASER_RESPALDOS:-$HOME/.renaser/respaldos}

log() { echo "== $* =="; }
falla() { echo "ERROR: $*" >&2; exit 1; }

# Toda llamada a AWS pasa por aqui: perfil y region explicitos, nunca los de por defecto.
aws_prod() { aws "$@" --profile "$PERFIL" --region "$REGION"; }

preparar() {
  local h
  for h in aws jq; do command -v "$h" >/dev/null || falla "falta $h en esta maquina."; done
  : "${RENASER_CUENTA_AWS:?exporta RENASER_CUENTA_AWS con el id de 12 digitos de la cuenta de produccion}"
  CUENTA=$(aws_prod sts get-caller-identity --query Account --output text)
  [ "$CUENTA" = "$RENASER_CUENTA_AWS" ] || falla "el perfil $PERFIL apunta a la cuenta $CUENTA, no a la esperada ($RENASER_CUENTA_AWS). No se cambio nada."
  echo "Cuenta verificada ($CUENTA, perfil $PERFIL, region $REGION). Modo: $([ "${APLICAR:-no}" = si ] && echo APLICAR || echo SIMULACRO)."
  NOMBRE=$(jq -r .Name "$PLANTILLA")
  TMP=$(mktemp -d)
  trap "rm -rf '$TMP'" EXIT
  umask 077
}

# Las reglas de la plantilla tal como las recibe AWS: cada excepcion de `Renaser.Excepciones` (una regla de un
# grupo administrado que acierta mal con trafico legitimo, FR-003) queda en Count dentro de su grupo; el grupo
# entero NUNCA se apaga.
reglas_para_aws() {
  jq '.Renaser.Excepciones as $ex
      | [ .Rules[]
          | .Name as $nombre
          | if .Statement.ManagedRuleGroupStatement then
              ([ $ex[] | select(.Grupo == $nombre) | {Name: .Regla, ActionToUse: {Count: {}}} ]) as $o
              | (if ($o | length) > 0 then .Statement.ManagedRuleGroupStatement.RuleActionOverrides = $o else . end)
            else . end ]' "$PLANTILLA"
}

# Lo que recibe `create-web-acl`: la plantilla sin lo que es solo de Renaser.
entrada_de_creacion() {
  jq --argjson reglas "$(reglas_para_aws)" 'del(.Renaser) | .Rules = $reglas' "$PLANTILLA"
}

# El Web ACL que ya existe con ese nombre (vacio si no hay): {Name, Id, ARN, LockToken, ...}.
acl_existente() {
  aws_prod wafv2 list-web-acls --scope "$ALCANCE" | jq -c --arg n "$NOMBRE" '[.WebACLs[] | select(.Name == $n)] | first // empty'
}

# Lo que recibe `update-web-acl` a partir de lo que devuelve `get-web-acl` (que no es lo mismo).
entrada_de_actualizacion() {
  jq --arg alcance "$ALCANCE" '
    {Name: .WebACL.Name, Scope: $alcance, Id: .WebACL.Id, DefaultAction: .WebACL.DefaultAction,
     VisibilityConfig: .WebACL.VisibilityConfig, Rules: .WebACL.Rules, LockToken: .LockToken}
    + (if .WebACL.Description then {Description: .WebACL.Description} else {} end)
    + (if .WebACL.CustomResponseBodies then {CustomResponseBodies: .WebACL.CustomResponseBodies} else {} end)'
}

respaldar() {  # <nombre del respaldo> <archivo>: copia con permiso 600 fuera del repositorio
  mkdir -p "$RESPALDOS"; chmod 700 "$RESPALDOS"
  RESPALDO="$RESPALDOS/$1-$(date -u +%Y%m%dT%H%M%SZ).json"
  cp "$2" "$RESPALDO"; chmod 600 "$RESPALDO"
  echo "Respaldo en $RESPALDO"
}

resumen_de_reglas() {  # <get-web-acl o entrada con Rules>: una linea por regla
  jq -r '(.WebACL.Rules // .Rules)[]
         | "  \(.Priority)  \(.Name)  \(
             if .Statement.RateBasedStatement then "tasa \(.Statement.RateBasedStatement.Limit)/\(.Statement.RateBasedStatement.EvaluationWindowSec)s por \(.Statement.RateBasedStatement.AggregateKeyType)"
             else "grupo \(.Statement.ManagedRuleGroupStatement.Name)" end)  ->  \(
             if .Action then (.Action | keys[0]) else "grupo: \(.OverrideAction | keys[0] | if . == "Count" then "Count" else "Block (acciones propias del grupo)" end)" end)"' "$1"
}
