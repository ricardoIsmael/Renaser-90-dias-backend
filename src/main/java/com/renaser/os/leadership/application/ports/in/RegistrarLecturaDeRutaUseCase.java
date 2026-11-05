package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.habits.api.RutaDeCarreraConsultadaEvent;

/**
 * Anota en {@code consultas_de_ficha} que alguien abrió la ruta de una carrera con GPS de otra persona (spec 042, FR-041). Lo dispara
 * el evento que publica {@code habits} al leerla. Idempotente por {@code consultaId}: el outbox puede entregarlo más de una vez.
 */
public interface RegistrarLecturaDeRutaUseCase {

    void registrar(RutaDeCarreraConsultadaEvent lectura);
}
