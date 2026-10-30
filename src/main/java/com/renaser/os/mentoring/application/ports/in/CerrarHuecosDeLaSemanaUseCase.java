package com.renaser.os.mentoring.application.ports.in;

/**
 * El barrido que guarda la foto de la semana que acaba de terminar de cada persona (spec 015, FR-018). Lo llama el
 * scheduler cada hora: el domingo de cada persona termina a una hora UTC distinta según su zona, así que el dominio
 * decide con quién hay algo que hacer.
 */
public interface CerrarHuecosDeLaSemanaUseCase {

    ResultadoDelCierre cerrarPendientes();

    /**
     * @param evaluados personas con programa activado y cuenta activa que se miraron
     * @param guardadas fotos que se guardaron en esta corrida
     * @param fallidas  personas cuyo cálculo falló: se reintentan en la próxima corrida
     */
    record ResultadoDelCierre(int evaluados, int guardadas, int fallidas) {

        public static final ResultadoDelCierre VACIO = new ResultadoDelCierre(0, 0, 0);

        public ResultadoDelCierre mas(ResultadoDelCierre otro) {
            return new ResultadoDelCierre(evaluados + otro.evaluados, guardadas + otro.guardadas,
                    fallidas + otro.fallidas);
        }
    }
}
