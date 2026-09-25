package com.renaser.os.points.application.ports.in.semaforo;

/**
 * El barrido horario del semáforo (D-168): calcula los días cerrados que faltan de cada persona
 * medida y cierra las semanas sábado→viernes que ya terminaron. Nada se calcula al subir un hábito.
 */
public interface CerrarSemaforoUseCase {

    ResultadoDelCierre cerrarPendientes();

    /**
     * @param evaluados      personas con programa activado y cuenta activa que se revisaron
     * @param diasGuardados  días que se insertaron o cambiaron
     * @param semanasCerradas fotos semanales nuevas
     * @param fallidos       personas que fallaron (se reintentan solas en la próxima corrida)
     */
    record ResultadoDelCierre(int evaluados, int diasGuardados, int semanasCerradas, int fallidos) {

        public static final ResultadoDelCierre VACIO = new ResultadoDelCierre(0, 0, 0, 0);

        public ResultadoDelCierre mas(ResultadoDelCierre otro) {
            return new ResultadoDelCierre(evaluados + otro.evaluados, diasGuardados + otro.diasGuardados,
                    semanasCerradas + otro.semanasCerradas, fallidos + otro.fallidos);
        }
    }
}
