package com.renaser.os.rocks.api;

import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;
import com.renaser.os.rocks.domain.model.rocasemanal.VentanaPlanificacionSemanal;
import com.renaser.os.shared.domain.UserId;

/**
 * Contrato publico de {@code rocks} para CORREGIR un objetivo semanal ya creado (D-177, herramienta
 * {@code proponer_editar_objetivo_semanal} del acompanante, {@code rag}).
 *
 * <p><b>Delega, no reimplementa.</b> Corre {@code EditarDentroDe48hUseCase}, el mismo de
 * {@code PATCH /rocks/weekly/{id}}: dueno, cuenta activa, largos y escala se deciden ahi. Aca se
 * resuelve que roca semanal es la del eje pedido en esa semana, porque quien llama no conoce los ids.
 *
 * <p><b>La ventana de edicion se mira ANTES de llamar al caso de uso</b>, con la misma regla de dominio
 * que usa el dashboard ({@code VentanaPlanificacionSemanal.puedeEditar}), para que el rechazo diga el
 * motivo real ({@link MotivoRechazo#VENTANA_CERRADA}) y no se confunda con una cuenta sin acceso: el
 * caso de uso lanza la misma excepcion para las dos cosas.
 */
public interface EdicionDeObjetivoSemanalPort {

    /**
     * La ventana de W-03, para que quien llama pueda explicar un {@link MotivoRechazo#VENTANA_CERRADA}
     * con la regla real y no con una copia: salen de {@code VentanaPlanificacionSemanal}.
     */
    int VENTANA_ABRE_DOMINGO_HORA = VentanaPlanificacionSemanal.ABRE_HORA_DOMINGO;
    int VENTANA_CIERRA_LUNES_HORA = VentanaPlanificacionSemanal.CIERRA_HORA_LUNES;
    int MARGEN_TARDIO_HORAS = (int) VentanaPlanificacionSemanal.MARGEN_TARDIO.toHours();

    /**
     * La ultima semana del programa ({@code SemanaPrograma.ULTIMA_SEMANA}): quien propone "la semana
     * siguiente" no puede pedir una 14 (D-203, E-320).
     */
    int ULTIMA_SEMANA = SemanaPrograma.ULTIMA_SEMANA;

    /**
     * @param numeroSemana la semana de programa que la persona vio al proponer
     * @param eje          nombre de la constante de {@code EjeObjetivo}
     */
    ResultadoEdicion editar(UserId aprendizId, int numeroSemana, String eje, CambioDelObjetivo cambio);

    /** Cada campo en {@code null} se deja como esta (PATCH parcial, igual que la app). */
    record CambioDelObjetivo(String titulo, String obstaculo, String contingencia, Integer autoevaluacionInicio) {

        public boolean vacio() {
            return titulo == null && obstaculo == null && contingencia == null && autoevaluacionInicio == null;
        }
    }

    sealed interface ResultadoEdicion {

        record Editado(String eje) implements ResultadoEdicion {
        }

        record Rechazado(MotivoRechazo motivo) implements ResultadoEdicion {
        }
    }

    /** Por que {@code rocks} no guardo la edicion. */
    enum MotivoRechazo {
        /** El participante no existe. */
        SIN_PROGRAMA,
        /** Cuenta suspendida, o sin el programa de rocas andando. */
        SIN_ACCESO,
        /** El eje no tiene objetivo en esa semana. */
        SIN_OBJETIVO_SEMANAL,
        /**
         * Paso la ventana de rectificacion (W-03, RK-5): la del Domingo Ritual (domingo 12:00 a lunes
         * 09:00) o, si se creo fuera de ella, 2 horas desde que se creo sin pasar de ese dia.
         */
        VENTANA_CERRADA,
        /** Nada que cambiar, eje inexistente, texto demasiado largo, autoevaluacion fuera de 1-10. */
        DATOS_INVALIDOS
    }
}
