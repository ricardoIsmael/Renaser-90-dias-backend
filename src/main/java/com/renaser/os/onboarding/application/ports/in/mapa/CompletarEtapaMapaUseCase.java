package com.renaser.os.onboarding.application.ports.in.mapa;

import com.renaser.os.shared.domain.UserId;

/**
 * Marca el Mapa como etapa terminada — lo que hace que la pantalla "Tu proceso completo" pase de
 * "1 de 2" a "2 de 2".
 *
 * <p><b>No es lo mismo que ACTIVAR el mapa</b> (fase 4 del plan): activar crea los habitos. Esto
 * solo registra que la persona recorrió la etapa.
 */
public interface CompletarEtapaMapaUseCase {

    void completar(UserId actorId);
}
