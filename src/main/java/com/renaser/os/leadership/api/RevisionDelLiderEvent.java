package com.renaser.os.leadership.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * «Tu líder revisó a tu grupo»: lo que el líder de mentores miró hoy en las fichas de las personas de un mentor, para
 * que ese mentor lo sepa (spec 005, FR-017). Sale una vez al día —se intenta dos veces entre las 20:00 y las 20:59 de la zona del mentor—, y solo cuenta las
 * consultas del líder: Administración y Alquimista abren la misma ficha y no avisan.
 *
 * <p>Evento y no llamada directa a {@code notifications}, por los mismos dos motivos que
 * {@code ResumenSemanalDelGrupoEvent}: la dirección de las dependencias y el aislamiento del barrido (un mentor que falla
 * no frena a los demás).
 *
 * <p>El cuerpo ya viene redactado con <b>solo el primer nombre</b> de las personas ({@code "Hoy revisó a Ana y Luis"}):
 * sale por push y no lleva apellidos ni ninguna métrica. El detalle se ve en «Mi grupo», con permisos revalidados.
 *
 * @param claveDeduplicacion {@code UUID.nameUUIDFromBytes("consultas-lider:" + mentorId + ":" + dia)}: una por mentor
 *                           y día local, para que repetir la corrida no cree otro aviso
 * @param grupoId            el grupo del mentor en el que más personas se miraron: es la ruta del aviso
 * @param personas           cuántas personas distintas se miraron (las que el cuerpo nombra más las que cuenta)
 * @param dia                el día local del mentor que se resume
 */
public record RevisionDelLiderEvent(UUID claveDeduplicacion, UUID mentorId, UUID grupoId, String cuerpo, int personas,
                                    LocalDate dia, Instant generadoEn) {

    /** Ruta profunda a «Mi grupo» del grupo de la ruta. El cliente revalida permisos al abrirla. */
    public String rutaApp() {
        return "/mentor/groups/" + grupoId + "/semaforo";
    }
}
