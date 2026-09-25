package com.renaser.os.rag.application.ports.out.rocas;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Lo que el acompanante necesita de {@code rocks} para CERRAR la semana (2026-09-23, Domingo Ritual,
 * herramienta {@code proponer_cerrar_semana}): la revision de cada objetivo semanal. Solo lo llama
 * {@code CerrarSemanaConfirmable}, o sea despues de que la persona toco "Confirmar" (D-153).
 *
 * <p>Las reglas (escala, textos obligatorios, dueno, cuenta activa) las corre {@code rocks} con
 * {@code CerrarSemanaUseCase}, el de la app. El rechazo vuelve como {@link ResultadoCierre.Rechazado}.
 */
public interface CerrarSemanaDeRocasPort {

    /** Los ejes y la escala de autoevaluacion que acepta {@code rocks}, para validar la forma antes de proponer. */
    ReglasDelCierre reglas();

    /** @param numeroSemana la semana de programa que se le mostro a la persona al proponer */
    ResultadoCierre cerrarSemana(UserId aprendizId, int numeroSemana, List<RevisionDelEje> revisiones);

    record ReglasDelCierre(List<String> ejes, int autoevaluacionMinima, int autoevaluacionMaxima) {
    }

    /** @param autoevaluacion como le fue en ese eje, en la escala de {@link ReglasDelCierre} */
    record RevisionDelEje(String eje, int autoevaluacion, String bloqueoPrincipal, String correccion) {
    }

    sealed interface ResultadoCierre {

        record Cerrada(List<String> ejes) implements ResultadoCierre {
        }

        record Rechazado(Motivo motivo) implements ResultadoCierre {
        }
    }

    /** Espejo de {@code rocks.api.CierreDeSemanaPort.MotivoRechazo}. */
    enum Motivo {
        SIN_PROGRAMA,
        SIN_ACCESO,
        SIN_OBJETIVO_SEMANAL,
        YA_REVISADA,
        DATOS_INVALIDOS
    }
}
