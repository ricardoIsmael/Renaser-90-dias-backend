package com.renaser.os.onboarding.application.ports.in.caja;

/** El barrido horario de la caja (D-219, spec §5): los avisos de «en revisión» y de «¿ya te llegó?». */
public interface BarrerCajasUseCase {

    ResultadoDelBarrido barrer();

    record ResultadoDelBarrido(int evaluadas, int avisos, int fallidas) {
    }
}
