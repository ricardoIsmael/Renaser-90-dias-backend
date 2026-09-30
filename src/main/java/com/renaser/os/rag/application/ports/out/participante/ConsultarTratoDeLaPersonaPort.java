package com.renaser.os.rag.application.ports.out.participante;

import com.renaser.os.shared.domain.UserId;

/**
 * Como tratar a la persona en lo que escribe el acompanante (E-457). Lo resuelve {@code onboarding}
 * con la pregunta {@code sex} de la ficha inicial; sin dato, {@link TratoDeLaPersona#NEUTRO}.
 */
public interface ConsultarTratoDeLaPersonaPort {

    TratoDeLaPersona de(UserId participanteId);

    enum TratoDeLaPersona {
        MASCULINO, FEMENINO, NEUTRO
    }
}
