package com.renaser.os.habits.application.ports.in.registro;

/**
 * El barrido que pasa a {@code EXPIRADO} lo {@code PENDIENTE} de los dias que ya terminaron.
 *
 * <p><b>E-534 (2026-10-05).</b> Antes se llamaba {@code expirarPendientesAnterioresA(LocalDate hoy)} y recibia UNA
 * fecha para todo el padron: la UTC de las 05:00 UTC. Eso solo es la medianoche de quien esta en UTC−5 (Lima); al
 * oeste vencia lo de HOY antes de tiempo y al este lo de ayer horas tarde. Ahora cada registro vence segun el dia
 * local de SU participante ({@code domain.model.registro.CorteDeExpiracion}), y el barrido corre cada hora.
 */
public interface ExpirarRegistrosVencidosUseCase {

    /**
     * Pasa a {@code EXPIRADO} lo {@code PENDIENTE} de cada participante cuyo dia ya termino EN SU ZONA. Sin penalizacion
     * (0 puntos, como siempre). Derivado e idempotente: correrlo dos veces da lo mismo, y correrlo tarde se pone al dia.
     * Pagina el padron; cada fila se guarda en su propia transaccion y un participante que falla no frena a los demas.
     */
    ResultadoDelBarrido expirarDiasTerminados();

    /**
     * @param participantes participantes con algo pendiente que se evaluaron
     * @param expirados     registros que pasaron a {@code EXPIRADO}
     * @param fallidos      participantes o registros que fallaron: quedan para la proxima corrida
     */
    record ResultadoDelBarrido(int participantes, int expirados, int fallidos) {

        public static final ResultadoDelBarrido VACIO = new ResultadoDelBarrido(0, 0, 0);

        public ResultadoDelBarrido mas(ResultadoDelBarrido otro) {
            return new ResultadoDelBarrido(participantes + otro.participantes, expirados + otro.expirados,
                    fallidos + otro.fallidos);
        }
    }
}
