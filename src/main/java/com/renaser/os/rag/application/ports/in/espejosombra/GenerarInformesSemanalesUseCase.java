package com.renaser.os.rag.application.ports.in.espejosombra;

/**
 * El barrido del Espejo Sombra: genera el informe de la semana que, en la zona de cada participante, ya llegó a su
 * corte (E-560). Se invoca cada hora y es seguro repetirlo: decide por participante, y un informe que ya existe no
 * se regenera.
 *
 * <p>No se expone por REST: la única forma de invocarlo es {@code GenerarInformesSemanalesScheduler}.
 */
public interface GenerarInformesSemanalesUseCase {

    void generarLosQueTocan();
}
