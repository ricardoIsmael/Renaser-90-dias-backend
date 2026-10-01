package com.renaser.os.community.domain.model.acompanamiento;

import com.renaser.os.community.domain.model.celula.CelulaId;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Decide qué corresponde hacer con un aprendiz: entrar a recepción, pasar al grupo estable,
 * esperar o quedarse donde está.
 *
 * <p>Puro a propósito. Recibe el día de programa <b>ya calculado en la zona del participante</b>
 * y la ocupación de los grupos; no consulta base ni reloj. Así el caso de las 04:59 UTC en
 * Lima —que todavía es el día anterior— se prueba sin levantar nada.
 */
public final class PlanificadorDeTraslado {

    private PlanificadorDeTraslado() {
    }

    public enum DestinoTraslado {
        /** Entra a la recepción de su cohorte. */
        RECEPCION,
        /** Pasa al grupo estable elegido. */
        GRUPO_ESTABLE,
        /** Le tocaba grupo pero no hay cupo. Conserva lo que tiene y soporte se entera (P-04). */
        ESPERANDO_GRUPO,
        /**
         * Le tocaba grupo y la cohorte no tiene NINGUNO en curso (D-240). Se queda en la recepción y
         * se avisa al administrador y al líder de mentores: los grupos los crea el administrador a
         * mano, y trasladar a un grupo cerrado o programado lo dejaba en un grupo que no ve.
         */
        SIN_GRUPO_EN_CURSO,
        /** La cohorte no tiene recepción designada: es un fallo de configuración, no del aprendiz. */
        SIN_RECEPCION_CONFIGURADA,
        /** Nada que hacer. */
        SIN_CAMBIO
    }

    /**
     * @param diaDePrograma      día ya resuelto en la zona del participante.
     * @param programaActivado   una cuenta aprobada sin programa activado no arranca el reloj
     *                           (plan.md §4.1).
     * @param celulaActual       dónde está hoy, o {@code null}.
     * @param tipoActual         de qué tipo es esa célula, o {@code null}.
     */
    public record SituacionAprendiz(int diaDePrograma, boolean programaActivado, CelulaId celulaActual,
                                     TipoCelula tipoActual) {
    }

    /** Un grupo regular con su ocupación real y su tope efectivo. */
    public record GrupoCandidato(CelulaId id, int aprendices, int cupo) {

        boolean tieneLugar() {
            return aprendices < cupo;
        }
    }

    /**
     * @param conservaAccesoActual si el aprendiz mantiene el grupo/chat que ya tenía. Siempre
     *                             true salvo cuando efectivamente se lo cambia: nadie se queda
     *                             sin chat mientras se resuelve su traslado.
     */
    public record DecisionTraslado(DestinoTraslado destino, CelulaId grupoDestino, boolean conservaAccesoActual,
                                    String motivo) {
    }

    /**
     * @param gruposRegulares los grupos regulares de la cohorte que están EN CURSO hoy. Filtrarlos
     *                        es trabajo de quien llama (la vigencia necesita la zona de la cohorte);
     *                        acá una lista vacía significa «no hay ningún grupo en curso».
     */
    public static DecisionTraslado decidir(SituacionAprendiz situacion, PoliticaMentoria politica,
                                            CelulaId celulaRecepcion, List<GrupoCandidato> gruposRegulares) {
        if (!situacion.programaActivado()) {
            return sinCambio("El programa todavia no esta activado: el reloj no arranco");
        }

        boolean yaEnGrupoEstable = situacion.tipoActual() == TipoCelula.REGULAR;
        if (yaEnGrupoEstable) {
            // Aunque el dia haya bajado por una correccion de fecha. Devolver a recepcion a
            // alguien que ya tiene grupo le sacaria compañeros y chat por un ajuste
            // administrativo (plan.md §4, ultimo parrafo).
            return sinCambio("Ya pertenece a un grupo estable");
        }

        if (!politica.correspondeTraslado(situacion.diaDePrograma())) {
            if (situacion.tipoActual() == TipoCelula.RECEPCION) {
                return sinCambio("Ya esta en la recepcion de su cohorte");
            }
            if (celulaRecepcion == null) {
                return new DecisionTraslado(DestinoTraslado.SIN_RECEPCION_CONFIGURADA, null, true,
                        "La cohorte no tiene celula de recepcion designada");
            }
            return new DecisionTraslado(DestinoTraslado.RECEPCION, celulaRecepcion, true,
                    "Dia " + situacion.diaDePrograma() + ": corresponde recepcion");
        }

        if (gruposRegulares.isEmpty()) {
            return new DecisionTraslado(DestinoTraslado.SIN_GRUPO_EN_CURSO, null, true,
                    "No hay ningun grupo en curso en la cohorte: sigue en la recepcion");
        }
        return elegirGrupo(gruposRegulares)
                .map(destino -> new DecisionTraslado(DestinoTraslado.GRUPO_ESTABLE, destino.id(), false,
                        "Dia " + situacion.diaDePrograma() + ": corresponde grupo estable"))
                .orElseGet(() -> new DecisionTraslado(DestinoTraslado.ESPERANDO_GRUPO, null, true,
                        "No hay grupo con cupo en la cohorte"));
    }

    /**
     * El grupo con menos aprendices y, ante empate, el de id menor.
     *
     * <p>Determinista por las dos razones que pide el plan: repetir el comando elige lo mismo,
     * y no depende del orden en que la base devolvió las filas. Llenar por el más vacío hace
     * que los grupos crezcan parejos en vez de completar uno y dejar el siguiente casi vacío.
     */
    private static Optional<GrupoCandidato> elegirGrupo(List<GrupoCandidato> candidatos) {
        return candidatos.stream()
                .filter(GrupoCandidato::tieneLugar)
                .min(Comparator.comparingInt(GrupoCandidato::aprendices)
                        .thenComparing(g -> g.id().value().toString()));
    }

    private static DecisionTraslado sinCambio(String motivo) {
        return new DecisionTraslado(DestinoTraslado.SIN_CAMBIO, null, true, motivo);
    }
}
