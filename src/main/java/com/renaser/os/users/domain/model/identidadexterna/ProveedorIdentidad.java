package com.renaser.os.users.domain.model.identidadexterna;

/**
 * Los tres proveedores de login social soportados (docs/MODULO_AUTH.md §6). Mismo enum que la
 * columna {@code identidades_externas.proveedor} representa como texto (GOOGLE | APPLE |
 * FACEBOOK) — ver migracion V3.
 */
public enum ProveedorIdentidad {
    GOOGLE,
    APPLE,
    FACEBOOK
}
