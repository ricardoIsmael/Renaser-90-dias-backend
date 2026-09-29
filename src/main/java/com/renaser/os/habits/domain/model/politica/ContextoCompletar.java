package com.renaser.os.habits.domain.model.politica;

import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;

import java.util.Objects;
import java.util.Optional;
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
 *
 * <p><b>La medicion del dia (D-226).</b> Lleva tambien el numero que la persona escribio al completar
 * (los km de {@code DAILY_KM}), si escribio alguno. Es otro hecho que el catalogo no sabe y que una
 * politica mira para decidir ({@code PoliticaKilometros}: mayor que cero y con tope). No es perezoso
 * porque no cuesta nada: ya llego en el pedido.
 */
public final class ContextoCompletar {

    private final BooleanSupplier consulta;
    private final MedicionDiaria medicion;
    private Boolean respuesta;

    private ContextoCompletar(BooleanSupplier consulta, MedicionDiaria medicion) {
        this.consulta = consulta;
        this.medicion = medicion;
    }

    /**
     * @param publicoEnElMuroEseDia si el participante publico en el Muro dentro del dia de
     *                              ejecucion del registro, EN SU ZONA HORARIA (quien arma el
     *                              contexto es responsable de esa conversion — regla
     *                              02-tiempo-zonas-y-schedulers)
     */
    public static ContextoCompletar de(BooleanSupplier publicoEnElMuroEseDia) {
        return de(publicoEnElMuroEseDia, null);
    }

    /** @param medicion lo que la persona escribio al completar, o {@code null} si no escribio nada */
    public static ContextoCompletar de(BooleanSupplier publicoEnElMuroEseDia, MedicionDiaria medicion) {
        Objects.requireNonNull(publicoEnElMuroEseDia, "publicoEnElMuroEseDia es obligatorio");
        return new ContextoCompletar(publicoEnElMuroEseDia, medicion);
    }

    /** Solo la medicion, para las pruebas de una politica que no mira el Muro. */
    public static ContextoCompletar conMedicion(MedicionDiaria medicion) {
        return new ContextoCompletar(sinHechosExternos().consulta, medicion);
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
        }, null);
    }

    /** El numero del dia que la persona escribio al completar; vacio si no escribio ninguno. */
    public Optional<MedicionDiaria> medicion() {
        return Optional.ofNullable(medicion);
    }

    public boolean publicoEnElMuroEseDia() {
        if (respuesta == null) {
            respuesta = consulta.getAsBoolean();
        }
        return respuesta;
    }
}
