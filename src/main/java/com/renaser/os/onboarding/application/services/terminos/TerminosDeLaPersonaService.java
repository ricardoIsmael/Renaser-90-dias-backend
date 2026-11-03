package com.renaser.os.onboarding.application.services.terminos;

import com.renaser.os.onboarding.application.ports.in.terminos.ObtenerTerminosUseCase;
import com.renaser.os.onboarding.application.ports.out.terminos.AceptacionesDeTerminosPort;
import com.renaser.os.onboarding.application.ports.out.terminos.AceptacionesDeTerminosPort.Aceptacion;
import com.renaser.os.onboarding.application.ports.out.terminos.AceptacionesDeTerminosPort.LoMostrado;
import com.renaser.os.onboarding.application.ports.out.terminos.TerminosPort;
import com.renaser.os.onboarding.domain.model.terminos.ContenidoDeTerminos;
import com.renaser.os.onboarding.domain.model.terminos.QuePideLaAceptacion;
import com.renaser.os.onboarding.domain.model.terminos.ReglasDeTerminos;
import com.renaser.os.onboarding.domain.model.terminos.VersionDeTerminos;
import com.renaser.os.shared.domain.DuracionDelPrograma;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.FormacionesFinder;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Los términos que le tocan a la persona (spec 027): los vigentes de SU formación, nunca los de otra (FR-022) y nunca un borrador (FR-004). Una lectura:
 * sin {@code @Transactional}. Los marcadores {@code {formacion}} y {@code {duracion}} del texto se reemplazan por los de la persona (FR-009).
 */
@Service
class TerminosDeLaPersonaService implements ObtenerTerminosUseCase {

    private final TerminosPort terminos;
    private final AceptacionesDeTerminosPort aceptaciones;
    private final AlcanceDeFormacionFinder formaciones;
    private final FormacionesFinder catalogo;
    private final ParticipacionProgramaFinder participaciones;
    private final FirmaDeLaAceptacion firmas;
    private final ArmadorDelAcuerdo acuerdos;

    TerminosDeLaPersonaService(TerminosPort terminos, AceptacionesDeTerminosPort aceptaciones, AlcanceDeFormacionFinder formaciones,
                               FormacionesFinder catalogo, ParticipacionProgramaFinder participaciones, FirmaDeLaAceptacion firmas, ArmadorDelAcuerdo acuerdos) {
        this.terminos = terminos;
        this.aceptaciones = aceptaciones;
        this.formaciones = formaciones;
        this.catalogo = catalogo;
        this.participaciones = participaciones;
        this.firmas = firmas;
        this.acuerdos = acuerdos;
    }

    @Override
    public TerminosParaLaPersona para(UserId persona) {
        Formacion formacion = formacionDe(persona);
        Optional<VersionDeTerminos> vigente = terminos.vigente(formacion);
        List<VersionDeTerminos> publicadas = terminos.publicadas(formacion);
        Optional<Integer> ultima = aceptaciones.ultimaAceptada(persona, formacion);
        QuePideLaAceptacion pide = ReglasDeTerminos.quePide(vigente, ultima, aceptaciones.aceptoAlgunaVez(persona), publicadas);
        Optional<String> nota = pide == QuePideLaAceptacion.NUEVA_VERSION ? ReglasDeTerminos.notaDelCambio(vigente, publicadas, ultima.orElse(0))
                : Optional.empty();
        String nombre = catalogo.de(formacion).nombre();
        Optional<AcuerdoDeLaPersona> acuerdo = vigente.map(v -> acuerdoDe(persona, formacion));
        return new TerminosParaLaPersona(formacion, nombre, vigente.map(v -> conMarcadores(v, nombre, persona)), pide, ultima, nota, acuerdo);
    }

    /**
     * «Mi aceptación» (spec 039): el nombre, el programa y las fechas. El cronograma de pagos no va (D-315, decisión del dueño del 2026-11-02): es asíncrono, lo llena
     * Administración después y la persona lo ve y lo confirma en «Mi ingreso» (spec 032), en el primer ingreso, al volver a aceptar o cuando sea.
     */
    private AcuerdoDeLaPersona acuerdoDe(UserId persona, Formacion formacion) {
        return acuerdos.armar(persona, formacion);
    }

    @Override
    public Optional<TerminosAceptados> aceptados(UserId persona) {
        Formacion formacion = formacionDe(persona);
        List<Aceptacion> suyas = aceptaciones.delUsuario(persona);
        Optional<Aceptacion> ultima = suyas.stream().filter(a -> a.formacion() == formacion).findFirst().or(() -> suyas.stream().findFirst());
        return ultima.flatMap(a -> terminos.porId(a.versionId()).map(v -> new TerminosAceptados(conMarcadores(v, catalogo.de(v.formacion()).nombre(), persona),
                a.aceptadaEn(), firmas.urlDe(a.firmaMediaId()), loMostrado(a))));
    }

    /** Lo que se le mostró en «Mi aceptación» al aceptar; vacío en lo aceptado antes de la spec 039. */
    private static Optional<AcuerdoAceptado> loMostrado(Aceptacion aceptacion) {
        LoMostrado mostrado = aceptacion.mostrado();
        return mostrado.hayAlgo() ? Optional.of(new AcuerdoAceptado(mostrado.programa(), mostrado.inicio(), mostrado.fin())) : Optional.empty();
    }

    /** La formación de los términos de la persona: la suya (la activa, si es personal); Administración y Alquimista, que no tienen una, firman los de la Escuela. */
    private Formacion formacionDe(UserId persona) {
        return formaciones.formacionDe(persona).orElse(Formacion.POR_DEFECTO);
    }

    private VersionDeTerminos conMarcadores(VersionDeTerminos version, String nombreDeLaFormacion, UserId persona) {
        int duracion = participaciones.deParticipante(persona).map(p -> p.duracionDias()).orElse(DuracionDelPrograma.POR_DEFECTO);
        ContenidoDeTerminos c = version.contenido();
        return version.conContenido(new ContenidoDeTerminos(reemplazar(c.titulo(), nombreDeLaFormacion, duracion),
                reemplazar(c.introduccion(), nombreDeLaFormacion, duracion), c.clausulas().stream().map(t -> reemplazar(t, nombreDeLaFormacion, duracion)).toList(),
                reemplazar(c.textoCasilla(), nombreDeLaFormacion, duracion)), version.actualizadaEn());
    }

    private static String reemplazar(String texto, String formacion, int duracionDias) {
        return texto.replace("{formacion}", formacion).replace("{duracion}", DuracionDelPrograma.etiqueta(duracionDias));
    }
}
