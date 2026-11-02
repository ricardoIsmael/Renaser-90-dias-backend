package com.renaser.os.onboarding.application.ports.in.terminos;

import com.renaser.os.onboarding.application.ports.in.pagos.CronogramaParaFirmarUseCase.CronogramaParaFirmar;
import com.renaser.os.onboarding.domain.model.terminos.QuePideLaAceptacion;
import com.renaser.os.onboarding.domain.model.terminos.VersionDeTerminos;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Los términos que le tocan a la persona: los vigentes de SU formación (spec 027, FR-010, FR-022) y lo que debe hacer con ellos. Releer lo aceptado (FR-023).
 */
public interface ObtenerTerminosUseCase {

    /** Lo vigente para la persona y qué se le pide; los marcadores {@code {formacion}} y {@code {duracion}} ya van reemplazados. */
    TerminosParaLaPersona para(UserId persona);

    /** El texto de la versión que la persona aceptó por última vez, aunque haya una más nueva; vacío si nunca aceptó. */
    Optional<TerminosAceptados> aceptados(UserId persona);

    /**
     * @param vigente  vacío si su formación todavía no publicó términos (la app usa el texto que lleva)
     * @param nota     lo que cambió, solo cuando se le pide aceptar de nuevo
     * @param acuerdo  «Mi aceptación» (spec 039); vacío si no hay términos que mostrar
     */
    record TerminosParaLaPersona(Formacion formacion, String nombreDeLaFormacion, Optional<VersionDeTerminos> vigente, QuePideLaAceptacion pide,
                                 Optional<Integer> ultimaAceptada, Optional<String> nota, Optional<AcuerdoDeLaPersona> acuerdo) {
    }

    /**
     * «Mi aceptación» del Acuerdo de transformación (spec 039): lo que el sistema llena con los datos reales de la persona, antes de que firme.
     *
     * @param participante el nombre completo de su perfil
     * @param programa     «Formación RENASER · 90 días», «Diplomado RENASER · 3 meses»…; solo el nombre del programa si no tiene uno que mostrar
     * @param inicio       el que tiene si ya activó; si no, el que tendrá (mañana en su zona); {@code null} si no hay un programa que prometerle
     * @param fin          el último día de su programa; {@code null} cuando {@code inicio} lo es
     * @param empezado     si ya activó su programa
     * @param cronograma   el cronograma a firmar junto con los términos: solo en el primer ingreso de quien tiene uno sin confirmar
     */
    record AcuerdoDeLaPersona(String participante, String programa, LocalDate inicio, LocalDate fin, boolean empezado, Optional<CronogramaParaFirmar> cronograma) {

        public AcuerdoDeLaPersona conCronograma(Optional<CronogramaParaFirmar> cronogramaAFirmar) {
            return new AcuerdoDeLaPersona(participante, programa, inicio, fin, empezado, cronogramaAFirmar);
        }
    }

    /**
     * @param firmaUrl enlace de lectura de la firma que dibujó al aceptar (vence a los 15 minutos); vacío si no quedó firma o el archivo ya no está
     * @param acuerdo  lo que se le mostró en «Mi aceptación» al aceptar (spec 039); vacío en lo aceptado antes de esa spec
     */
    record TerminosAceptados(VersionDeTerminos version, Instant aceptadaEn, Optional<String> firmaUrl, Optional<AcuerdoAceptado> acuerdo) {
    }

    /** @param cronogramaFirmado si firmó su cronograma de pagos junto con los términos */
    record AcuerdoAceptado(String programa, LocalDate inicio, LocalDate fin, boolean cronogramaFirmado) {
    }
}
