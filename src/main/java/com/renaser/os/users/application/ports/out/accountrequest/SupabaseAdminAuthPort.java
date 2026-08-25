package com.renaser.os.users.application.ports.out.accountrequest;

import com.renaser.os.shared.domain.UserId;

/**
 * Lo que la aplicacion necesita de Supabase Admin Auth para las transacciones
 * compensatorias de §5.3.3/§9.1 (si el alta falla despues de crear el usuario en
 * Supabase, o si se rechaza una solicitud, hay que liberar el email).
 *
 * SIN ADAPTADOR TODAVIA: no hay credenciales de Supabase Admin API confirmadas en
 * este entorno. El puerto existe para que los casos de uso ya queden bien formados;
 * conectar el adaptador real es trabajo de infraestructura, no de dominio.
 */
public interface SupabaseAdminAuthPort {

    void deleteUser(UserId userId);
}
