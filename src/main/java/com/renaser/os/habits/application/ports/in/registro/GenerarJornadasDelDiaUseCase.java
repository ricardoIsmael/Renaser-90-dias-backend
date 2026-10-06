package com.renaser.os.habits.application.ports.in.registro;

/**
 * Etapa 1 de zonas (E-556) — el barrido que arma el dia de cada participante cuando empieza SU dia, no a una hora UTC
 * fija. Corre cada hora; quien ya tiene su dia armado no se toca, y a quien le toca se le arma en su zona.
 */
public interface GenerarJornadasDelDiaUseCase {

    /**
     * Para cada participante activo cuyo hoy (en su zona) todavia no tiene registros: hace regir sus cambios de horario
     * programados para ese dia y le genera el dia. <b>Idempotente</b>: correrlo dos veces en la misma hora, o tarde,
     * da lo mismo.
     */
    ResultadoDelBarrido generarLasQueYaEmpezaron();

    /**
     * @param participantes cuantos participantes reviso
     * @param generados     a cuantos se les armo el dia en esta corrida
     * @param fallidos      a cuantos no se pudo (se reintenta la proxima hora)
     */
    record ResultadoDelBarrido(int participantes, int generados, int fallidos) {

        public static final ResultadoDelBarrido VACIO = new ResultadoDelBarrido(0, 0, 0);

        public ResultadoDelBarrido mas(ResultadoDelBarrido otro) {
            return new ResultadoDelBarrido(participantes + otro.participantes, generados + otro.generados,
                    fallidos + otro.fallidos);
        }
    }
}
