package com.renaser.os.users.application.ports.out.matriz;

import java.util.Optional;

/**
 * El modo que el dueño fijó en caliente para la matriz de MENTOR y TRAINEE_GUIDE (spec 050, tramo H, FR-041). Cambiarlo no exige
 * reiniciar: es un valor en Redis que el interceptor relee cada pocos segundos. Si no hay valor, rige el de la configuración
 * ({@code renaser.security.mentor-enforcement}).
 */
public interface ModoDeLaMatrizFijadoPort {

    /** El modo fijado, o vacío si no hay ninguno o no es válido. Lanza una excepción si no puede leer; quien llama conserva lo último que supo. */
    Optional<ModoDeLaMatriz> fijado();
}
