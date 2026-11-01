package com.renaser.os.onboarding.application.services.terminos;

import com.renaser.os.onboarding.application.ports.in.terminos.GestionarTerminosUseCase;
import com.renaser.os.onboarding.application.ports.out.terminos.TerminosPort;
import com.renaser.os.onboarding.domain.model.terminos.ContenidoDeTerminos;
import com.renaser.os.onboarding.domain.model.terminos.VersionDeTerminos;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Editar y publicar los términos de una formación (spec 027, FR-001 a FR-008). Cada formación tiene sus propios términos: lo que se hace en una nunca toca
 * a las otras (FR-020). El borrador es uno por formación y lo ven solo Administración y Alquimista; publicar lo convierte en la versión siguiente.
 *
 * <p>{@code baseNumero} es la versión vigente que quien edita tenía a la vista: si cuando guarda o publica ya es otra, alguien publicó mientras tanto y se le
 * avisa en vez de pisar en silencio lo publicado (FR-008).
 */
@Service
class EdicionDeTerminosService implements GestionarTerminosUseCase {

    private static final String PUBLICARON_MIENTRAS_EDITABAS = "Otra persona publicó una versión mientras editabas. Revisa los cambios antes de seguir.";

    private final GuardiaDeTerminos guardia;
    private final TerminosPort terminos;
    private final IdGenerator ids;
    private final Clock clock;

    EdicionDeTerminosService(GuardiaDeTerminos guardia, TerminosPort terminos, IdGenerator ids, Clock clock) {
        this.guardia = guardia;
        this.terminos = terminos;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional
    public VersionDeTerminos guardarBorrador(UserId actor, Formacion formacion, ContenidoDeTerminos contenido, Integer baseNumero) {
        guardia.exigirAdministracion(actor);
        contenido.exigirQueSePuedaGuardar();
        exigirQueNadieHayaPublicado(formacion, baseNumero);
        VersionDeTerminos borrador = terminos.borrador(formacion).map(actual -> actual.conContenido(contenido, clock.now()))
                .orElseGet(() -> VersionDeTerminos.borrador(ids.newId(), formacion, contenido, baseNumero, clock.now()));
        terminos.guardar(borrador);
        return borrador;
    }

    @Override
    @Transactional
    public void descartarBorrador(UserId actor, Formacion formacion) {
        guardia.exigirAdministracion(actor);
        terminos.descartarBorrador(formacion);
    }

    @Override
    @Transactional
    public VersionDeTerminos restaurarComoBorrador(UserId actor, Formacion formacion, int numero) {
        guardia.exigirAdministracion(actor);
        VersionDeTerminos vieja = terminos.publicada(formacion, numero)
                .orElseThrow(() -> new NoSuchElementException("No existe la versión " + numero + " de esos términos."));
        Integer base = terminos.vigente(formacion).map(VersionDeTerminos::numero).orElse(null);
        terminos.descartarBorrador(formacion);
        VersionDeTerminos borrador = VersionDeTerminos.borrador(ids.newId(), formacion, vieja.contenido(), base, clock.now());
        terminos.guardar(borrador);
        return borrador;
    }

    @Override
    @Transactional
    public VersionDeTerminos publicar(UserId actor, Formacion formacion, String nota, boolean pideNuevaAceptacion, Integer baseNumero) {
        guardia.exigirAdministracion(actor);
        exigirQueNadieHayaPublicado(formacion, baseNumero);
        VersionDeTerminos borrador = terminos.borrador(formacion)
                .orElseThrow(() -> new IllegalStateException("No hay un borrador que publicar. Edita los términos primero."));
        var faltas = borrador.contenido().loQueFaltaParaPublicar();
        if (!faltas.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", faltas));
        }
        Optional<VersionDeTerminos> vigente = terminos.vigente(formacion);
        if (vigente.isPresent() && vigente.get().contenido().dicenLoMismo(borrador.contenido())) {
            throw new IllegalStateException("No hay cambios respecto de la versión vigente.");
        }
        int numero = vigente.map(v -> v.numero() + 1).orElse(1);
        VersionDeTerminos publicada = borrador.publicada(numero, nota, pideNuevaAceptacion, actor, clock.now());
        terminos.guardar(publicada);
        return publicada;
    }

    private void exigirQueNadieHayaPublicado(Formacion formacion, Integer baseNumero) {
        Integer vigente = terminos.vigente(formacion).map(VersionDeTerminos::numero).orElse(null);
        if (!Objects.equals(vigente, baseNumero)) {
            throw new IllegalStateException(PUBLICARON_MIENTRAS_EDITABAS);
        }
    }
}
