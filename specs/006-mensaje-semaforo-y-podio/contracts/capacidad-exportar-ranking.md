# Contrato: `capabilities.canExportRanking` en `GET /api/v1/mentor/context`

Campo **aditivo** en `capabilities` (junto a `canAdminister`).

```json
"capabilities": {
  "programRequired": false,
  "canStartProgram": false,
  "canAccompany": true,
  "canAdminister": true,
  "canExportRanking": true
}
```

## Reglas

- `true` solo si el rol de la cuenta tiene `Permission.EXPORT_RANKING`: MENTOR_LEAD, ADMIN, ALCHEMIST e IT_SUPPORT.
  - Para TRAINEE, TRAINEE_GUIDE y MENTOR es `false`.
  - Para una cuenta `SUSPENDED` es `false`.
- La app lo lee como `.nullish()`: si no viene (backend anterior), no hay botón. Es el lado seguro.
- No hay endpoint de exportación nuevo. La imagen se arma en el teléfono con `GET /api/v1/ranking`, que ya es de
  `USE_APP`.

## Pruebas de autorización

- Un test por rol de `canExportRanking`: verdadero o falso, según la matriz.
- La cuenta `SUSPENDED` recibe 403 en `/mentor/context`, como hoy.
