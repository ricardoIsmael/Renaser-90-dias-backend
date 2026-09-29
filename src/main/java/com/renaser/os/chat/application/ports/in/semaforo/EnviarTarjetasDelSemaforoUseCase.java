package com.renaser.os.chat.application.ports.in.semaforo;

/**
 * La tarjeta diaria del semáforo en el chat de soporte (D-223): a las 23:50 de cada aprendiz, la imagen del
 * color de su día y «Hoy llevas 85 % de tus hábitos.», firmadas por el programa. La llama un barrido cada
 * 5 minutos; quién está en su hora lo decide cada zona ({@code HoraDeLaTarjeta}).
 */
public interface EnviarTarjetasDelSemaforoUseCase {

    /** Manda las que tocan en este momento. Idempotente: correrlo dos veces no duplica nada. */
    ResultadoDeTarjetas enviarLasQueTocan();

    /**
     * @param enSuHora  aprendices activos que estaban en su ventana de 23:50–23:59
     * @param enviadas  tarjetas que salieron en esta corrida
     * @param fallidas  personas que fallaron (se reintentan en la corrida siguiente de la ventana)
     */
    record ResultadoDeTarjetas(int enSuHora, int enviadas, int fallidas) {

        public static final ResultadoDeTarjetas NADA = new ResultadoDeTarjetas(0, 0, 0);

        public ResultadoDeTarjetas mas(ResultadoDeTarjetas otro) {
            return new ResultadoDeTarjetas(enSuHora + otro.enSuHora, enviadas + otro.enviadas, fallidas + otro.fallidas);
        }
    }
}
