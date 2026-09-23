package com.renaser.os.rocks.api;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Contrato publico de {@code rocks} para CERRAR la semana desde otro modulo (2026-09-23, herramienta
 * {@code proponer_cerrar_semana} del acompanante, {@code rag}): la revision del Domingo Ritual (W-04)
 * de cada objetivo semanal.
 *
 * <p><b>Por que un archivo aparte y no un metodo mas de {@link PlanificacionDeRocasPort}.</b> Planificar
 * crea rocas; cerrar escribe la revision de rocas que ya existen, con otro caso de uso, otros datos y
 * otros rechazos. Juntarlos obligaba a mezclar dos enums de motivos en un solo contrato.
 *
 * <p><b>Delega, no reimplementa.</b> Cada revision corre el MISMO caso de uso que
 * {@code PATCH /rocks/weekly/{id}/review} ({@code CerrarSemanaUseCase}): cuenta activa, dueno de la roca,
 * escala 1-10 y textos obligatorios se deciden ahi. Aca solo se resuelve que roca semanal es la del eje
 * pedido en esa semana, porque quien llama no conoce los ids de {@code rocks}.
 *
 * <p><b>Lo unico que este contrato agrega: no pisa una revision.</b> El caso de uso es idempotente y
 * sobreescribe (asi lo usa la app para corregir). Desde el chat eso haria perder en silencio lo que la
 * persona escribio en la app entre que se propuso y se confirmo, asi que un eje ya revisado vuelve como
 * {@link MotivoRechazo#YA_REVISADA} y no se escribe nada. Corregir una revision sigue siendo desde la app.
 *
 * <p><b>Todo o nada ante un rechazo.</b> Todas las revisiones se validan (comando incluido) antes de
 * escribir la primera; lo unico que puede dejar una a medias es un error de infraestructura, que sube.
 */
public interface CierreDeSemanaPort {

    /** La escala de {@code CerrarSemanaCommand.autoevaluacionFin}; {@code CierreDeSemanaServiceTest} rompe si cambia. */
    int AUTOEVALUACION_MINIMA = 1;
    int AUTOEVALUACION_MAXIMA = 10;

    /**
     * @param numeroSemana la semana de programa que la persona vio al proponer (no "la de hoy" al
     *                     confirmar: una propuesta del domingo 23:55 confirmada el lunes 00:05 cierra
     *                     la semana que se le mostro)
     */
    ResultadoCierre cerrarSemana(UserId aprendizId, int numeroSemana, List<RevisionDelEje> revisiones);

    /**
     * La revision de un eje, tal como la recibe {@code CerrarSemanaUseCase.CerrarSemanaCommand}.
     *
     * @param eje nombre de la constante de {@code EjeObjetivo} ({@link PlanificacionDeRocasPort#EJES})
     */
    record RevisionDelEje(String eje, int autoevaluacionFin, String bloqueoPrincipal, String correccion) {
    }

    sealed interface ResultadoCierre {

        /** @param ejes los ejes cuya revision se guardo, en el orden pedido */
        record Cerrada(List<String> ejes) implements ResultadoCierre {
        }

        record Rechazado(MotivoRechazo motivo) implements ResultadoCierre {
        }
    }

    /** Por que {@code rocks} no guardo el cierre. */
    enum MotivoRechazo {
        /** El participante no existe. */
        SIN_PROGRAMA,
        /** Cuenta suspendida, o sin el programa de rocas andando. */
        SIN_ACCESO,
        /** Algun eje pedido no tiene objetivo semanal en esa semana. */
        SIN_OBJETIVO_SEMANAL,
        /** Algun eje pedido ya tiene su revision: no se pisa desde aca. */
        YA_REVISADA,
        /** Eje repetido o inventado, autoevaluacion fuera de escala, bloqueo o correccion vacios. */
        DATOS_INVALIDOS
    }
}
