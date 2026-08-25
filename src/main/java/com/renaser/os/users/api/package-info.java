/**
 * Unico paquete de `users` visible desde otros modulos (CLAUDE.MD §5.1). Todo lo demas
 * (domain/, application/, infrastructure/) es interno — ArchitectureTest.
 * modulesDoNotLeakInternals rompe el build si algun otro modulo lo importa directo.
 */
@org.springframework.modulith.NamedInterface("api")
package com.renaser.os.users.api;
