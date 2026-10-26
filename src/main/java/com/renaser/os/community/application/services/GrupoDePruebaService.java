package com.renaser.os.community.application.services;

import com.renaser.os.community.api.CelulaCreadaEvent;
import com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent;
import com.renaser.os.community.api.GrupoDePruebaUseCase;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SaveAsignacionPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.celula.SaveCelulaPort;
import com.renaser.os.community.application.ports.out.cohorte.LoadCohortePort;
import com.renaser.os.community.application.ports.out.cohorte.SaveCohortePort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionId;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.GrupoDePrueba;
import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AsignacionCelulaPort;
import com.renaser.os.users.api.CuentasDePruebaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * El «Grupo de prueba (ver como)» (ver {@link GrupoDePruebaUseCase}; D-273). Escribe como el sistema (actor
 * {@code null}, como el ingreso a la recepción), con la misma fuente de verdad que todo grupo: las asignaciones con sus
 * intervalos, los punteros de {@code participantes_programa} y {@code celulas.mentor_id}.
 *
 * <p>Eventos: {@link CelulaCreadaEvent} al crear el grupo (el chat crea su conversación) y
 * {@link ComposicionDeCelulaCambiadaEvent} cuando cambia quién está (el chat suma a los integrantes y abre los chats de
 * dos mentor–aprendiz). Todo eso queda entre cuentas de prueba. La bienvenida del grupo, si está prendida, también.
 */
@Service
class GrupoDePruebaService implements GrupoDePruebaUseCase {

    private final LoadCohortePort loadCohorte;
    private final SaveCohortePort saveCohorte;
    private final LoadCelulaPort loadCelula;
    private final SaveCelulaPort saveCelula;
    private final LoadAsignacionesPort loadAsignaciones;
    private final SaveAsignacionPort saveAsignacion;
    private final AsignacionCelulaPort punteros;
    private final UserSummaryFinder usuarios;
    private final CuentasDePruebaFinder cuentasDePrueba;
    private final ApplicationEventPublisher eventos;
    private final IdGenerator idGenerator;
    private final Clock clock;

    @SuppressWarnings("java:S107") // colaboradores, no parámetros de negocio
    GrupoDePruebaService(LoadCohortePort loadCohorte, SaveCohortePort saveCohorte, LoadCelulaPort loadCelula,
                         SaveCelulaPort saveCelula, LoadAsignacionesPort loadAsignaciones,
                         SaveAsignacionPort saveAsignacion, AsignacionCelulaPort punteros, UserSummaryFinder usuarios,
                         CuentasDePruebaFinder cuentasDePrueba, ApplicationEventPublisher eventos,
                         IdGenerator idGenerator, Clock clock) {
        this.loadCohorte = loadCohorte;
        this.saveCohorte = saveCohorte;
        this.loadCelula = loadCelula;
        this.saveCelula = saveCelula;
        this.loadAsignaciones = loadAsignaciones;
        this.saveAsignacion = saveAsignacion;
        this.punteros = punteros;
        this.usuarios = usuarios;
        this.cuentasDePrueba = cuentasDePrueba;
        this.eventos = eventos;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID asegurar(UserId soporteTi, UserId mentor, Collection<UserId> aprendices, Collection<UserId> guias) {
        exigirSoporteTi(soporteTi);
        exigirSoloCuentasDePrueba(Objects.requireNonNull(mentor, "mentor es obligatorio"), aprendices, guias);
        Instant ahora = clock.now();
        asegurarCohorte(ahora);
        Celula grupo = asegurarGrupo(ahora);
        boolean cambio = asegurarMentor(grupo, mentor, ahora);
        for (UserId aprendiz : aprendices) {
            cambio |= asegurarIntegrante(aprendiz, FuncionAcompanamiento.APRENDIZ, ahora);
        }
        for (UserId guia : guias) {
            cambio |= asegurarIntegrante(guia, FuncionAcompanamiento.GUIA, ahora);
        }
        for (UserId aprendiz : aprendices) {
            punteros.sincronizarAcompanamiento(aprendiz, GrupoDePrueba.CELULA.value(), mentor);
        }
        if (cambio) {
            eventos.publishEvent(new ComposicionDeCelulaCambiadaEvent(GrupoDePrueba.CELULA.value(), ahora));
        }
        return GrupoDePrueba.CELULA.value();
    }

    private void exigirSoporteTi(UserId soporteTi) {
        boolean esSoporteTiActivo = soporteTi != null && usuarios.findById(soporteTi)
                .filter(u -> u.role() == UserRole.IT_SUPPORT && u.status() == UserStatus.ACTIVE).isPresent();
        if (!esSoporteTiActivo) {
            throw new NotAuthorizedException("Solo Soporte TI arma el grupo de prueba");
        }
    }

    /** El cinturón de esta clase: escribe sin los guards de Administración, así que nunca toca a una persona real. */
    private void exigirSoloCuentasDePrueba(UserId mentor, Collection<UserId> aprendices, Collection<UserId> guias) {
        Set<UserId> todos = new HashSet<>(aprendices);
        todos.addAll(guias);
        todos.add(mentor);
        if (!cuentasDePrueba.entre(todos).containsAll(todos)) {
            throw new IllegalArgumentException("En el grupo de prueba solo entran cuentas de prueba");
        }
    }

    private void asegurarCohorte(Instant ahora) {
        if (loadCohorte.porId(GrupoDePrueba.COHORTE).isEmpty()) {
            saveCohorte.save(Cohorte.crear(GrupoDePrueba.COHORTE, GrupoDePrueba.NOMBRE_DE_LA_COHORTE,
                    ahora.atZone(ZoneId.of("America/Lima")).toLocalDate(), null, ahora));
        }
    }

    private Celula asegurarGrupo(Instant ahora) {
        return loadCelula.porId(GrupoDePrueba.CELULA).orElseGet(() -> {
            Celula creado = saveCelula.save(Celula.crear(GrupoDePrueba.CELULA, GrupoDePrueba.NOMBRE_DEL_GRUPO,
                    GrupoDePrueba.COHORTE, null, null, ahora));
            eventos.publishEvent(new CelulaCreadaEvent(GrupoDePrueba.CELULA.value(), ahora));
            return creado;
        });
    }

    /** El mentor de prueba lidera el grupo: si lo lideraba otro (alguien lo cambió a mano), se cierra su jefatura. */
    private boolean asegurarMentor(Celula grupo, UserId mentor, Instant ahora) {
        List<AsignacionCelula> jefaturas = vigentesDelGrupo(FuncionAcompanamiento.MENTOR, ahora);
        boolean yaLoLidera = mentor.equals(grupo.mentorId()) && jefaturas.size() == 1
                && jefaturas.getFirst().usuarioId().equals(mentor);
        if (yaLoLidera) {
            return false;
        }
        for (AsignacionCelula saliente : jefaturas) {
            saliente.cerrar(ahora, MotivoAsignacion.ADMINISTRATIVO);
            saveAsignacion.save(saliente);
        }
        abrir(mentor, FuncionAcompanamiento.MENTOR, ahora);
        grupo.asignarMentor(mentor, ahora);
        saveCelula.save(grupo);
        return true;
    }

    /** Un aprendiz está en un grupo a la vez: si estaba en otro (de prueba), se lo cierra antes de abrir este. */
    private boolean asegurarIntegrante(UserId usuario, FuncionAcompanamiento funcion, Instant ahora) {
        boolean yaEsta = loadAsignaciones.porUsuario(usuario).stream().anyMatch(a -> a.funcion() == funcion
                && a.celulaId().equals(GrupoDePrueba.CELULA) && a.vigenteEn(ahora));
        if (yaEsta) {
            return false;
        }
        if (funcion == FuncionAcompanamiento.APRENDIZ) {
            loadAsignaciones.porUsuario(usuario).stream()
                    .filter(a -> a.funcion() == FuncionAcompanamiento.APRENDIZ && a.vigenteEn(ahora))
                    .forEach(otra -> {
                        otra.cerrar(ahora, MotivoAsignacion.ADMINISTRATIVO);
                        saveAsignacion.save(otra);
                    });
        }
        abrir(usuario, funcion, ahora);
        return true;
    }

    private List<AsignacionCelula> vigentesDelGrupo(FuncionAcompanamiento funcion, Instant ahora) {
        return loadAsignaciones.porCelula(GrupoDePrueba.CELULA).stream()
                .filter(a -> a.funcion() == funcion && a.vigenteEn(ahora)).toList();
    }

    /** La clave lleva el instante: una cuenta que salió del grupo y vuelve abre un intervalo nuevo, no repite el viejo. */
    private void abrir(UserId usuario, FuncionAcompanamiento funcion, Instant ahora) {
        saveAsignacion.save(AsignacionCelula.abrir(AsignacionId.of(idGenerator.newId()), GrupoDePrueba.CELULA,
                usuario, funcion, ahora, MotivoAsignacion.ADMINISTRATIVO, null,
                "ver-como|" + funcion + "|" + usuario.value() + "|" + ahora.toEpochMilli()));
    }
}
