package com.renaser.os.rag.application.ports.out.ia;

import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.memoria.MemoriaDeRenasia;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;

import java.util.List;
import java.util.Objects;

/**
 * "Abri una conversacion por voz con el acompanante": un modelo que escucha y habla a la vez
 * (D-162; hoy Gemini Live). Se le manda el audio de la persona y devuelve audio mientras lo genera,
 * con la transcripcion de las dos partes y los pedidos de herramienta.
 *
 * <p><b>El audio es siempre PCM de 16 bits, mono, 16 kHz, en los dos sentidos.</b> Si el proveedor
 * habla a otra frecuencia (Gemini devuelve 24 kHz), la conversion es del adaptador: la aplicacion y
 * la app trabajan con un solo formato.
 *
 * <p><b>El prompt de sistema lo arma el adaptador</b>, igual que en {@link ChatIAPort}: las
 * plantillas viven en {@code src/main/resources/prompts/} y son del adaptador. Aca viaja solo lo que
 * decide el negocio: donde esta parada la persona y que herramientas tiene el acompanante.
 *
 * <p><b>Nunca dentro de una transaccion</b> (C-1): una sesion dura minutos.
 */
public interface ConversacionEnVivoPort {

    /** {@code false} con el interruptor apagado o sin credencial: la app usa el flujo anterior. */
    boolean disponible();

    /**
     * Abre la sesion y espera a que el modelo la acepte (Gemini tarda ~4 s).
     *
     * @throws ConversacionEnVivoNoDisponibleException si no se pudo abrir
     */
    SesionEnVivo abrir(Apertura apertura, Oyente oyente);

    /**
     * @param situacion    {@code null} si quien habla no cursa el programa (igual que en {@link ChatIAPort})
     * @param herramientas las mismas del chat del acompanante
     * @param memoria      lo que el acompanante sabe de la persona (D-167); {@code null} con la memoria
     *                     apagada, y entonces el prompt queda como antes
     */
    record Apertura(SituacionDelAprendiz situacion, List<DefinicionHerramienta> herramientas,
                    MemoriaDeRenasia memoria) {
        public Apertura {
            herramientas = List.copyOf(Objects.requireNonNull(herramientas, "herramientas es obligatorio"));
        }

        /** Sin memoria (D-167). */
        public Apertura(SituacionDelAprendiz situacion, List<DefinicionHerramienta> herramientas) {
            this(situacion, herramientas, null);
        }
    }

    /**
     * Lo que el modelo va diciendo. El adaptador llama a estos metodos de a uno por vez, nunca en
     * paralelo, en el orden en que llegan.
     */
    interface Oyente {

        void audio(byte[] pcm16kHz);

        void oido(String texto);

        void dicho(String texto);

        void interrumpido();

        void turnoCompleto();

        /** El {@code id} lo pone el modelo y hay que devolverlo tal cual en la respuesta. */
        void pedidoDeHerramienta(String id, InvocacionHerramienta invocacion);

        /** La sesion se cerro del lado del proveedor (o se corto la conexion). */
        void cerrada(String motivo);
    }

    /** La sesion abierta. */
    interface SesionEnVivo {

        void enviarAudio(byte[] pcm16kHz);

        void responderHerramienta(String id, String nombre, ResultadoHerramienta resultado);

        void cerrar();
    }

    /** No se pudo abrir la sesion. El mensaje es tecnico: va al log, nunca a la persona. */
    class ConversacionEnVivoNoDisponibleException extends RuntimeException {
        public ConversacionEnVivoNoDisponibleException(String mensaje) {
            super(mensaje);
        }
    }
}
