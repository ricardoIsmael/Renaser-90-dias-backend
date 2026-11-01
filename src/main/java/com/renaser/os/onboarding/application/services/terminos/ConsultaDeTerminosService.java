package com.renaser.os.onboarding.application.services.terminos;

import com.renaser.os.onboarding.application.ports.in.terminos.ConsultarTerminosUseCase;
import com.renaser.os.onboarding.application.ports.out.terminos.AceptacionesDeTerminosPort;
import com.renaser.os.onboarding.application.ports.out.terminos.TerminosPort;
import com.renaser.os.onboarding.domain.model.terminos.VersionDeTerminos;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FormacionesFinder;
import com.renaser.os.users.api.IntegrantesDeFormacionFinder;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/** Lo que Administración y Alquimista leen de los términos (spec 027): resumen, historial, quiénes aceptaron y la constancia de una persona. Solo lecturas. */
@Service
class ConsultaDeTerminosService implements ConsultarTerminosUseCase {

    private final GuardiaDeTerminos guardia;
    private final TerminosPort terminos;
    private final AceptacionesDeTerminosPort aceptaciones;
    private final FormacionesFinder catalogo;
    private final IntegrantesDeFormacionFinder integrantes;
    private final UserSummaryFinder usuarios;
    private final FirmaDeLaAceptacion firmas;

    ConsultaDeTerminosService(GuardiaDeTerminos guardia, TerminosPort terminos, AceptacionesDeTerminosPort aceptaciones, FormacionesFinder catalogo,
                              IntegrantesDeFormacionFinder integrantes, UserSummaryFinder usuarios, FirmaDeLaAceptacion firmas) {
        this.guardia = guardia;
        this.terminos = terminos;
        this.aceptaciones = aceptaciones;
        this.catalogo = catalogo;
        this.integrantes = integrantes;
        this.usuarios = usuarios;
        this.firmas = firmas;
    }

    @Override
    public List<ResumenDeTerminos> resumen(UserId actor) {
        guardia.exigirAdministracion(actor);
        return Arrays.stream(Formacion.values()).map(this::resumenDe).toList();
    }

    private ResumenDeTerminos resumenDe(Formacion formacion) {
        Optional<VersionDeTerminos> vigente = terminos.vigente(formacion);
        long aceptantes = vigente.map(v -> aceptaciones.aceptantesPorVersion(formacion).getOrDefault(v.id(), 0L)).orElse(0L);
        return new ResumenDeTerminos(formacion, catalogo.de(formacion).nombre(), vigente, terminos.borrador(formacion).isPresent(), aceptantes);
    }

    @Override
    public DetalleDeTerminos detalle(UserId actor, Formacion formacion) {
        guardia.exigirAdministracion(actor);
        List<VersionDeTerminos> publicadas = terminos.publicadas(formacion);
        Map<java.util.UUID, Long> cuentas = aceptaciones.aceptantesPorVersion(formacion);
        Map<UserId, UserSummary> autores = usuarios.findByIds(publicadas.stream().map(VersionDeTerminos::publicadaPor).filter(java.util.Objects::nonNull).toList());
        List<VersionDelHistorial> historial = publicadas.stream().sorted(Comparator.comparing(VersionDeTerminos::numero).reversed())
                .map(v -> new VersionDelHistorial(v, cuentas.getOrDefault(v.id(), 0L), nombreDe(autores, v.publicadaPor()))).toList();
        return new DetalleDeTerminos(formacion, catalogo.de(formacion).nombre(), terminos.vigente(formacion), terminos.borrador(formacion), historial);
    }

    @Override
    public VersionDeTerminos version(UserId actor, Formacion formacion, int numero) {
        guardia.exigirAdministracion(actor);
        return terminos.publicada(formacion, numero).orElseThrow(() -> new NoSuchElementException("No existe la versión " + numero + " de esos términos."));
    }

    @Override
    public QuienesAceptaron quienes(UserId actor, Formacion formacion, int numero) {
        guardia.exigirAdministracion(actor);
        terminos.publicada(formacion, numero).orElseThrow(() -> new NoSuchElementException("No existe la versión " + numero + " de esos términos."));
        Set<UserId> aceptaron = aceptaciones.quienesAceptaronDesde(formacion, numero);
        Set<UserId> faltan = new HashSet<>(integrantes.deLaFormacion(formacion));
        faltan.removeAll(aceptaron);
        Map<UserId, UserSummary> nombres = usuarios.findByIds(java.util.stream.Stream.concat(aceptaron.stream(), faltan.stream()).toList());
        return new QuienesAceptaron(numero, personas(aceptaron, nombres), personas(faltan, nombres));
    }

    @Override
    public List<ConstanciaDeUnaPersona> constanciaDe(UserId actor, UserId persona) {
        guardia.exigirAdministracion(actor);
        return aceptaciones.delUsuario(persona).stream().map(a -> new ConstanciaDeUnaPersona(a.formacion(), a.numero(), a.aceptadaEn(), firmas.urlDe(a.firmaMediaId()).orElse(null)))
                .toList();
    }

    private static List<Persona> personas(Set<UserId> ids, Map<UserId, UserSummary> nombres) {
        return ids.stream().map(id -> new Persona(id, nombreDe(nombres, id))).sorted(Comparator.comparing(p -> p.nombre().toLowerCase())).toList();
    }

    private static String nombreDe(Map<UserId, UserSummary> nombres, UserId id) {
        UserSummary resumen = id == null ? null : nombres.get(id);
        return resumen == null ? null : resumen.fullName();
    }
}
