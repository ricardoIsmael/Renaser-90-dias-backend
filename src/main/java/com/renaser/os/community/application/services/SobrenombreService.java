package com.renaser.os.community.application.services;

import com.renaser.os.community.api.SobrenombreCambiadoPorAdministracionEvent;
import com.renaser.os.community.application.ports.in.sobrenombre.AdministrarSobrenombresUseCase;
import com.renaser.os.community.application.ports.in.sobrenombre.MiSobrenombreUseCase;
import com.renaser.os.community.application.ports.out.sobrenombre.SobrenombresPort;
import com.renaser.os.community.application.ports.out.sobrenombre.SobrenombresPort.FilaDeLaLista;
import com.renaser.os.community.application.ports.out.sobrenombre.SobrenombresPort.FilaDeSobrenombre;
import com.renaser.os.community.domain.model.sobrenombre.PalabrasProhibidasBase;
import com.renaser.os.community.domain.model.sobrenombre.Sobrenombre;
import com.renaser.os.community.domain.model.sobrenombre.SobrenombreRechazadoException;
import com.renaser.os.community.domain.model.sobrenombre.SobrenombreRechazadoException.Motivo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Spec 046 (D-331): el sobrenombre en el Muro.
 *
 * <p>Reglas (decisiones del dueño del 06/11): no es obligatorio (D8); el personal también puede tener uno (D9); se cambia una vez
 * cada 30 días (D5; Administración, cuando quiera); es único dentro de la formación (D6) y no puede ser el nombre real de otra
 * persona; si Administración lo cambia, se le avisa a la persona sin el motivo (D7).
 *
 * <p>La administración (ver, cambiar, quitar y editar las palabras) es de quien modera el Muro: ADMIN y ALCHEMIST activos, el mismo
 * criterio de {@code PublicacionMuroService.requireModerador}. Lo exige este servicio, no solo el permiso del endpoint: el servicio es la
 * segunda línea de defensa que tienen los demás servicios de administración (spec 050, FR-042).
 */
@Service
public class SobrenombreService implements MiSobrenombreUseCase, AdministrarSobrenombresUseCase {

    static final Duration ESPERA_ENTRE_CAMBIOS = Duration.ofDays(30);
    private static final int LIMITE_DE_LA_LISTA = 200;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-PE"));
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final SobrenombresPort sobrenombres;
    private final AlcanceDeFormacionFinder formaciones;
    private final UserSummaryFinder usuarios;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public SobrenombreService(SobrenombresPort sobrenombres, AlcanceDeFormacionFinder formaciones, UserSummaryFinder usuarios,
                              ApplicationEventPublisher eventos, Clock clock) {
        this.sobrenombres = sobrenombres;
        this.formaciones = formaciones;
        this.usuarios = usuarios;
        this.eventos = eventos;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- la propia persona

    @Override
    @Transactional(readOnly = true)
    public MiSobrenombre mio(UserId persona) {
        return vista(sobrenombres.de(persona).orElse(null));
    }

    @Override
    @Transactional
    public MiSobrenombre elegir(UserId persona, String crudo) {
        FilaDeSobrenombre actual = sobrenombres.de(persona).orElse(null);
        Instant ahora = clock.now();
        // Issue #99: mandar el MISMO sobrenombre otra vez (doble toque, o reintento tras perder la respuesta) no es un cambio:
        // responde lo de siempre en vez de un 409 de espera.
        if (actual != null && actual.sobrenombre() != null && crudo != null
                && actual.sobrenombre().equals(Normalizer.normalize(crudo, Normalizer.Form.NFC).strip())) {
            return vista(actual);
        }
        Instant desde = puedeCambiarDesde(actual, ahora);
        if (desde != null) {
            throw new SobrenombreRechazadoException(Motivo.ESPERA,
                    "Puedes cambiarlo de nuevo desde el " + FECHA.format(desde.atZone(LIMA)) + ".");
        }
        Sobrenombre nuevo = validar(persona, crudo);
        if (actual != null && nuevo.texto().equals(actual.sobrenombre())) {
            return vista(actual);
        }
        sobrenombres.guardar(persona, nuevo, ahora, true);
        sobrenombres.anotarCambio(persona, actual == null ? null : actual.sobrenombre(), nuevo.texto(), persona, null, ahora);
        return mio(persona);
    }

    @Override
    @Transactional
    public MiSobrenombre quitar(UserId persona) {
        FilaDeSobrenombre actual = sobrenombres.de(persona).orElse(null);
        if (actual == null || actual.sobrenombre() == null) {
            return vista(actual);
        }
        Instant ahora = clock.now();
        sobrenombres.guardar(persona, null, ahora, true);
        sobrenombres.anotarCambio(persona, actual.sobrenombre(), null, persona, null, ahora);
        return mio(persona);
    }

    @Override
    @Transactional
    public MiSobrenombre descartarInvitacion(UserId persona) {
        sobrenombres.descartarInvitacion(persona, clock.now());
        return mio(persona);
    }

    // ---------------------------------------------------------------- Administración

    @Override
    @Transactional(readOnly = true)
    public String de(UserId actor, UserId persona) {
        exigirModerador(actor);
        formaciones.exigirQueLaVea(actor, persona);
        return sobrenombres.de(persona).map(FilaDeSobrenombre::sobrenombre).orElse(null);
    }

    @Override
    @Transactional
    public String cambiar(UserId actor, UserId persona, String nuevo, String motivo) {
        exigirModerador(actor);
        formaciones.exigirQueLaVea(actor, persona);
        String limpio = motivo == null ? "" : motivo.strip();
        if (limpio.length() < 3 || limpio.length() > 200) {
            throw new IllegalArgumentException("Escribe el motivo, de 3 a 200 letras.");
        }
        FilaDeSobrenombre actual = sobrenombres.de(persona).orElse(null);
        String antes = actual == null ? null : actual.sobrenombre();
        Instant ahora = clock.now();
        Sobrenombre sobrenombre = nuevo == null || nuevo.isBlank() ? null : validar(persona, nuevo);
        String despues = sobrenombre == null ? null : sobrenombre.texto();
        if (java.util.Objects.equals(antes, despues)) {
            return antes;
        }
        sobrenombres.guardar(persona, sobrenombre, ahora, false);
        sobrenombres.anotarCambio(persona, antes, despues, actor, limpio, ahora);
        eventos.publishEvent(new SobrenombreCambiadoPorAdministracionEvent(persona, despues == null, ahora));
        return despues;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonaConSobrenombre> listar(UserId actor, String buscar) {
        exigirModerador(actor);
        List<FilaDeLaLista> filas = sobrenombres.listar(buscar == null ? "" : Sobrenombre.normalizar(buscar), LIMITE_DE_LA_LISTA);
        Set<UserId> visibles = formaciones.dentroDelAlcance(filas.stream().map(FilaDeLaLista::persona).toList(),
                formaciones.de(actor));
        return filas.stream().filter(f -> visibles.contains(f.persona()))
                .map(f -> new PersonaConSobrenombre(f.persona(), f.sobrenombre(), f.nombreReal(), f.desde()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PalabrasProhibidas palabras(UserId actor) {
        exigirModerador(actor);
        return laLista();
    }

    @Override
    @Transactional
    public PalabrasProhibidas agregarPalabra(UserId actor, String palabra) {
        exigirModerador(actor);
        String normalizada = Sobrenombre.normalizar(palabra);
        if (normalizada.length() < 2 || normalizada.length() > 40) {
            throw new IllegalArgumentException("Escribe una palabra de 2 a 40 letras.");
        }
        sobrenombres.agregarPalabra(normalizada, actor, clock.now());
        return laLista();
    }

    @Override
    @Transactional
    public PalabrasProhibidas quitarPalabra(UserId actor, String palabra) {
        exigirModerador(actor);
        sobrenombres.quitarPalabra(Sobrenombre.normalizar(palabra));
        return laLista();
    }

    private PalabrasProhibidas laLista() {
        return new PalabrasProhibidas(PalabrasProhibidasBase.PALABRAS.size(), sobrenombres.palabrasDeAdministracion());
    }

    /**
     * Quien modera el Muro: ADMIN o ALCHEMIST con la cuenta activa, el mismo criterio de {@code PublicacionMuroService.esModerador}. Falla
     * cerrado: una cuenta que no existe recibe el mismo 403 que una que no modera, no un 404 que diría si existe.
     */
    private void exigirModerador(UserId actor) {
        boolean modera = usuarios.findById(actor)
                .map(u -> u.status() == UserStatus.ACTIVE && (u.role() == UserRole.ADMIN || u.role() == UserRole.ALCHEMIST))
                .orElse(false);
        if (!modera) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST administran los sobrenombres del Muro");
        }
    }

    // ---------------------------------------------------------------- reglas

    /** La forma, las palabras prohibidas, el nombre real de otra persona y que no lo tenga otra persona de sus formaciones (D6). */
    private Sobrenombre validar(UserId persona, String crudo) {
        List<String> prohibidas = new ArrayList<>(PalabrasProhibidasBase.PALABRAS);
        prohibidas.addAll(sobrenombres.palabrasDeAdministracion());
        Sobrenombre sobrenombre = Sobrenombre.de(crudo, prohibidas);
        sobrenombres.bloquear(sobrenombre.normalizado());
        if (sobrenombres.esNombreRealDeOtra(sobrenombre.normalizado(), persona)) {
            throw new SobrenombreRechazadoException(Motivo.REPETIDO, "Ese es el nombre de otra persona. Elige otro.");
        }
        Set<Formacion> mias = formaciones.formacionesDe(persona);
        for (UserId otra : sobrenombres.conElMismo(sobrenombre.normalizado(), persona)) {
            Set<Formacion> suyas = new HashSet<>(formaciones.formacionesDe(otra));
            suyas.retainAll(mias);
            if (!suyas.isEmpty() || mias.isEmpty()) {
                throw new SobrenombreRechazadoException(Motivo.REPETIDO, "Ese sobrenombre ya lo tiene otra persona.");
            }
        }
        return sobrenombre;
    }

    private static Instant puedeCambiarDesde(FilaDeSobrenombre fila, Instant ahora) {
        if (fila == null || fila.cambiadoPorLaPersonaEn() == null) {
            return null;
        }
        Instant desde = fila.cambiadoPorLaPersonaEn().plus(ESPERA_ENTRE_CAMBIOS);
        return desde.isAfter(ahora) ? desde : null;
    }

    private MiSobrenombre vista(FilaDeSobrenombre fila) {
        if (fila == null) {
            return new MiSobrenombre(null, null, false);
        }
        return new MiSobrenombre(fila.sobrenombre(), puedeCambiarDesde(fila, clock.now()), fila.invitacionDescartadaEn() != null);
    }
}
