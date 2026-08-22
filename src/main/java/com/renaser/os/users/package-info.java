/**
 * Modulo Users: fuente de verdad de identidad, roles y estado de cuenta (CLAUDE.MD §5.3).
 *
 * Solo el paquete users.api es visible desde otros modulos. ArchitectureTest rompe
 * el build si alguien importa users.domain / users.application desde afuera.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Users")
package com.renaser.os.users;
