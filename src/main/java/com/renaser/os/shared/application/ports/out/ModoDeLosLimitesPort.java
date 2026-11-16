package com.renaser.os.shared.application.ports.out;

import com.renaser.os.shared.domain.GrupoDeLimite;
import com.renaser.os.shared.domain.ModoDeLimite;

import java.util.Map;

/**
 * El modo que el dueño fijó en caliente para cada grupo (spec 050, FR-016 y Q6). Cambiarlo no exige reiniciar: es un valor en Redis
 * que la política relee cada pocos segundos. Si no hay valor para un grupo, rige el de la configuración.
 */
public interface ModoDeLosLimitesPort {

    /** Solo los grupos que tienen un modo fijado y válido. Lanza una excepción si no puede leer; quien llama conserva lo último que supo. */
    Map<GrupoDeLimite, ModoDeLimite> fijados();
}
