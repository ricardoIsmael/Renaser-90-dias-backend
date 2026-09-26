package com.renaser.os.rag.application.ports.out.rocas;

import com.renaser.os.shared.domain.UserId;

/**
 * Lo que el acompanante necesita de {@code rocks} para CORREGIR un objetivo semanal (D-177,
 * {@code proponer_editar_objetivo_semanal}). Solo lo llama {@code EditarObjetivoSemanalConfirmable},
 * despues de que la persona toco "Confirmar" (D-153).
 *
 * <p>La ventana de edicion, el dueno y los largos los decide {@code rocks} con
 * {@code EditarDentroDe48hUseCase}, el de la app.
 */
public interface EditarObjetivoSemanalPort {

    /** Cuando se puede editar, segun {@code rocks}: para explicar un rechazo con la regla real. */
    VentanaDeEdicion ventanaDeEdicion();

    /**
     * @param abreDomingoHora    hora local del domingo a la que abre la ventana del Domingo Ritual
     * @param cierraLunesHora    hora local del lunes a la que cierra
     * @param margenTardioHoras  horas para corregir un objetivo creado fuera de esa ventana
     */
    record VentanaDeEdicion(int abreDomingoHora, int cierraLunesHora, int margenTardioHoras) {
    }

    /** @param numeroSemana la semana de programa que se le mostro a la persona al proponer */
    Resultado editar(UserId aprendizId, int numeroSemana, String eje, Cambio cambio);

    /** Campo en {@code null} = no se toca. */
    record Cambio(String titulo, String obstaculo, String contingencia, Integer autoevaluacionInicio) {
    }

    sealed interface Resultado {

        record Editado(String eje) implements Resultado {
        }

        record Rechazado(Motivo motivo) implements Resultado {
        }
    }

    /** Espejo de {@code rocks.api.EdicionDeObjetivoSemanalPort.MotivoRechazo}. */
    enum Motivo {
        SIN_PROGRAMA,
        SIN_ACCESO,
        SIN_OBJETIVO_SEMANAL,
        VENTANA_CERRADA,
        DATOS_INVALIDOS
    }
}
