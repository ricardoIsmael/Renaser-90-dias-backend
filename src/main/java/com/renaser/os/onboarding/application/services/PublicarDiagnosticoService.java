package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.LectorDelDiagnostico;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.LecturaDelDiagnostico;
import com.renaser.os.onboarding.application.ports.in.diagnostico.DiagnosticoDeLaPersonaUseCase.DiagnosticoAbierto;
import com.renaser.os.onboarding.application.ports.in.diagnostico.EditarDiagnosticoUseCase.PreguntaAdministrada;
import com.renaser.os.onboarding.application.ports.in.diagnostico.PublicarDiagnosticoUseCase;
import com.renaser.os.onboarding.application.ports.out.diagnostico.BorradorDelDiagnosticoPort;
import com.renaser.os.onboarding.application.ports.out.diagnostico.ConsultarFormacionDeLaPersonaPort;
import com.renaser.os.onboarding.application.ports.out.diagnostico.VersionesDeFichaPort;
import com.renaser.os.onboarding.domain.model.diagnostico.VersionPublicada;
import com.renaser.os.onboarding.domain.model.diagnostico.FichaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.NoSePuedePublicarException;
import com.renaser.os.onboarding.domain.model.diagnostico.OtraPersonaPublicoException;
import com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion;
import com.renaser.os.onboarding.domain.model.diagnostico.PreguntaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.SeccionDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.TextoParaLaPersona;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Ver el borrador, previsualizarlo, publicarlo y exportarlo (spec 026, US5). Solo Administración y Alquimista. */
@Service
public class PublicarDiagnosticoService implements PublicarDiagnosticoUseCase {

    private static final int DURACION_POR_DEFECTO = 90;
    private static final String SIN_FORMACION = "Renaser";

    private final GuardiaDelDiagnostico guardia;
    private final BorradorDelDiagnosticoPort borrador;
    private final VersionesDeFichaPort versiones;
    private final ConsultarFormacionDeLaPersonaPort formaciones;
    private final DiagnosticoDeLaPersonaFinder lecturas;
    private final Clock clock;

    public PublicarDiagnosticoService(GuardiaDelDiagnostico guardia, BorradorDelDiagnosticoPort borrador, VersionesDeFichaPort versiones,
                                      ConsultarFormacionDeLaPersonaPort formaciones, DiagnosticoDeLaPersonaFinder lecturas, Clock clock) {
        this.guardia = guardia;
        this.borrador = borrador;
        this.versiones = versiones;
        this.formaciones = formaciones;
        this.lecturas = lecturas;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public BorradorAdministrado ver(UserId actor) {
        guardia.exigirQuePuedaAdministrar(actor);
        FichaDelDiagnostico ficha = borrador.cargar();
        Optional<VersionPublicada> ultima = versiones.ultima();
        Map<Integer, Integer> conteos = borrador.respuestasPorPregunta();
        List<SeccionAdministrada> secciones = ficha.secciones().stream()
                .map(s -> new SeccionAdministrada(s, s.preguntas().stream().map(p -> administrada(p, conteos)).toList())).toList();
        List<PreguntaAdministrada> retiradas = borrador.retiradas().stream().map(p -> administrada(p, conteos)).toList();
        boolean hayCambios = ultima.map(v -> !v.ficha().equals(ficha)).orElse(true);
        return new BorradorAdministrado(ultima.orElse(null), hayCambios, ficha.motivosParaNoPublicar(), secciones, retiradas);
    }

    @Override
    @Transactional(readOnly = true)
    public DiagnosticoAbierto vistaPrevia(UserId actor, Formacion formacion, Integer duracionDias) {
        guardia.exigirQuePuedaAdministrar(actor);
        int dias = duracionDias == null ? DURACION_POR_DEFECTO : duracionDias;
        String nombre = formacion == null ? SIN_FORMACION : formaciones.nombreDe(formacion);
        FichaDelDiagnostico ficha = borrador.cargar().paraLaFormacion(formacion)
                .conTextosPara(TextoParaLaPersona.duracionEnPalabras(dias), nombre);
        SeccionDelDiagnostico portada = ficha.portada().orElse(null);
        String titulo = portada == null ? "Diagnóstico inicial" : portada.titulo();
        return new DiagnosticoAbierto(versiones.ultima().map(VersionPublicada::version).orElse(0), "EN_CURSO", titulo, titulo,
                portada == null ? null : portada.descripcion(), formacion == null ? null : formacion.codigo(), nombre, dias,
                TextoParaLaPersona.duracionEnPalabras(dias), null, ficha, Map.of(), Map.of());
    }

    @Override
    @Transactional
    public VersionPublicada publicar(UserId actor, int versionBase) {
        guardia.exigirQuePuedaAdministrar(actor);
        FichaDelDiagnostico ficha = borrador.cargar();
        List<String> motivos = ficha.motivosParaNoPublicar();
        if (!motivos.isEmpty()) {
            throw new NoSePuedePublicarException(motivos);
        }
        Optional<VersionPublicada> ultima = versiones.ultima();
        int vigente = ultima.map(VersionPublicada::version).orElse(0);
        if (versionBase != vigente) {
            throw new OtraPersonaPublicoException(vigente);
        }
        if (ultima.isPresent() && ultima.get().ficha().equals(ficha)) {
            throw new IllegalStateException("No hay cambios sin publicar");
        }
        return versiones.publicar(ficha, actor, clock.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<VersionPublicada> versiones(UserId actor) {
        guardia.exigirQuePuedaAdministrar(actor);
        return versiones.listar();
    }

    @Override
    public String plantilla(UserId actor) {
        guardia.exigirQuePuedaAdministrar(actor);
        return PlantillaDeImportacion.plantilla();
    }

    @Override
    @Transactional(readOnly = true)
    public ExportacionDelDiagnostico exportar(UserId actor) {
        guardia.exigirQuePuedaAdministrar(actor);
        // El borrador se lee AQUÍ, dentro de esta transacción de lectura; las filas se escriben después, ya sin base de datos.
        FichaDelDiagnostico ficha = borrador.cargar();
        return escritorDeFila -> PlantillaDeImportacion.exportarPorFilas(ficha, escritorDeFila);
    }

    /** Deja constancia de quién lo abrió (spec 026): por eso no es de solo lectura. Administración llega a la persona sin pasar por un grupo. */
    @Override
    @Transactional
    public LecturaDelDiagnostico lecturaDe(UserId actor, UserId persona) {
        guardia.exigirQuePuedaAdministrar(actor);
        return lecturas.lecturaDe(persona, new LectorDelDiagnostico(actor, null, null));
    }

    private static PreguntaAdministrada administrada(PreguntaDelDiagnostico p, Map<Integer, Integer> conteos) {
        return new PreguntaAdministrada(p, conteos.getOrDefault(p.id(), 0), p.esProtegida());
    }
}
