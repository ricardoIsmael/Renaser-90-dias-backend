package com.renaser.os.habits.domain.model.politica;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Los hechos EXTERNOS al catalogo que una politica puede necesitar para decidir si un habito
 * se puede dar por cumplido.
 *
 * <p><b>Por que existe.</b> {@link PoliticaSantuario PoliticaSantuario} decide mirando solo
 * el habito: un BLOQUEO nunca se completa de un tirón, y eso se sabe sin consultar nada. La
 * regla que el dueno pidio para POST DIARIO EN COMUNIDAD no: depende de si el aprendiz
 * publico de verdad en el Muro, un hecho que vive en otro modulo. El contrato de
 * {@link PoliticaHabito} dice que las politicas son funciones puras y que "cualquier dato
 * externo que necesiten llega por parametro" — esta clase ES ese parametro. La politica
 * sigue sin conocer puertos, adaptadores ni `community`.
 *
 * <p><b>Por que PEREZOSO y no un boolean ya resuelto.</b> Resolver el hecho cuesta una
 * consulta, y completar un habito es el hot path del modulo: lo atraviesan los ~16 habitos
 * del dia de cada aprendiz, y todos menos uno caen en
 * {@link RegistroPoliticasHabito#GENERICA}, que no mira ningun hecho. Pasar un boolean ya
 * calculado obligaria a pagar esa consulta ~16 veces por dia por persona para que una sola
 * la use. Con el supplier, la consulta ocurre si y solo si una politica pregunta.
 *
 * <p>El supplier NO convierte a la politica en un objeto con I/O: la politica no elige a
 * quien le pregunta ni como se responde, igual que no elige de donde salio el {@code Habito}
 * que recibe. Quien orquesta ({@code RegistroService}) es el unico que sabe que detras hay
 * una consulta, y es el unico que puede saberlo.
 *
 * <p><b>Memoizado</b>: dos preguntas dentro de la misma decision no pueden dar respuestas
 * distintas ni cobrar dos consultas. Por eso NO es un record — un componente de record
 * expone el {@code BooleanSupplier} crudo y se perderia la memoizacion.
 */
public final class ContextoCompletar {

    private final BooleanSupplier consulta;
    private Boolean respuesta;

    private ContextoCompletar(BooleanSupplier consulta) {
        this.consulta = consulta;
    }

    /**
     * @param publicoEnElMuroEseDia si el participante publico en el Muro dentro del dia de
     *                              ejecucion del registro, EN SU ZONA HORARIA (quien arma el
     *                              contexto es responsable de esa conversion — regla
     *                              02-tiempo-zonas-y-schedulers)
     */
    public static ContextoCompletar de(BooleanSupplier publicoEnElMuroEseDia) {
        Objects.requireNonNull(publicoEnElMuroEseDia, "publicoEnElMuroEseDia es obligatorio");
        return new ContextoCompletar(publicoEnElMuroEseDia);
    }

    /**
     * Para las politicas que no miran ningun hecho externo y para los tests de las que
     * tampoco. Estalla si alguien igual pregunta: es un error de programacion (una politica
     * que necesita el hecho recibio un contexto que no lo tiene), no un "no publico".
     * Responder {@code false} en silencio le daria a un aprendiz un 400 inexplicable.
     */
    public static ContextoCompletar sinHechosExternos() {
        return new ContextoCompletar(() -> {
            throw new IllegalStateException(
                    "Esta politica necesita saber si el participante publico en el Muro, y el contexto no lo trae");
        });
    }

    public boolean publicoEnElMuroEseDia() {
        if (respuesta == null) {
            respuesta = consulta.getAsBoolean();
        }
        return respuesta;
    }
}
