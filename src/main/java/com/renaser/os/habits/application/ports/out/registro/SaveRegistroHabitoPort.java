package com.renaser.os.habits.application.ports.out.registro;

import com.renaser.os.habits.domain.model.registro.RegistroHabito;

public interface SaveRegistroHabitoPort {

    RegistroHabito save(RegistroHabito registro);

    /**
     * Guarda un track NUEVO solo si todavia no hay uno para ese participante, habito y fecha.
     * {@code false} si ya existia: por ejemplo, lo creo otro pedido simultaneo (E-230). Es la forma
     * idempotente de generar el dia: dos pedidos a la vez terminan igual que uno solo.
     */
    boolean insertarSiNoExiste(RegistroHabito registro);
}
