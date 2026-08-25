package com.renaser.os.habits.application.ports.out.santuario;

import com.renaser.os.habits.domain.model.santuario.RachaSinCelular;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelularId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

public interface LoadRachaSinCelularPort {

    Optional<RachaSinCelular> byId(RachaSinCelularId id);

    /** A lo sumo una racha ACTIVA por participante (unique index parcial en el baseline). */
    Optional<RachaSinCelular> activaDe(UserId participanteId);

    /** Version con bloqueo para cerrar/romper: evita doble otorgamiento por concurrencia. */
    Optional<RachaSinCelular> activaDeParaEscritura(UserId participanteId);

    List<RachaSinCelular> activasDe(List<UserId> participanteIds);
}
