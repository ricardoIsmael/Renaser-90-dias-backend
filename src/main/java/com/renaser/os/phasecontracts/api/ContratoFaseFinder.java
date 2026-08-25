package com.renaser.os.phasecontracts.api;

import com.renaser.os.shared.domain.UserId;

public interface ContratoFaseFinder {

    boolean estaFirmado(UserId participanteId, int numeroFase);
}
