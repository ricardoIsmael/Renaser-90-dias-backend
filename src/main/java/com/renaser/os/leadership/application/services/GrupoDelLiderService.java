package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.leadership.application.ports.in.ConsultarGrupoDelLiderUseCase;
import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;
import com.renaser.os.leadership.domain.model.cumplimiento.ConteoDeLaLista;
import com.renaser.os.leadership.domain.model.cumplimiento.OrdenDeLaListaDelLider;
import com.renaser.os.leadership.domain.model.cumplimiento.PersonaDeLaLista;
import com.renaser.os.leadership.domain.model.cumplimiento.VentanaDeLaFicha;
import com.renaser.os.mentoring.api.TablaDelSemaforoFinder;
import com.renaser.os.mentoring.api.TablaDelSemaforoFinder.FilaDeLaTabla;
import com.renaser.os.mentoring.api.TablaDelSemaforoFinder.TablaDeGrupo;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * La lista de un grupo para el líder (spec 005, US1): la tabla del semáforo del grupo —la misma que ve el mentor y el
 * administrador, sin una fórmula nueva (FR-008)— con el orden del líder y con quien lleva su propio programa en el
 * grupo, sin color.
 *
 * <p>Solo autoriza, arma y ordena: el color y el porcentaje salen de {@link TablaDelSemaforoFinder} y de la lectura
 * del semáforo de {@code points}. No registra nada: lo que se registra es abrir la ficha de una persona.
 */
@Service
public class GrupoDelLiderService implements ConsultarGrupoDelLiderUseCase {

    private final AccesoDeLiderazgo acceso;
    private final PersonasDelGrupo personas;
    private final SemaforoDeLaPersona semaforoDeLaPersona;
    private final TablaDelSemaforoFinder tablaDelSemaforo;
    private final UserSummaryFinder usuarios;
    private final Clock clock;

    GrupoDelLiderService(AccesoDeLiderazgo acceso, PersonasDelGrupo personas, SemaforoDeLaPersona semaforoDeLaPersona,
                         TablaDelSemaforoFinder tablaDelSemaforo, UserSummaryFinder usuarios, Clock clock) {
        this.acceso = acceso;
        this.personas = personas;
        this.semaforoDeLaPersona = semaforoDeLaPersona;
        this.tablaDelSemaforo = tablaDelSemaforo;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    /**
     * Sin {@code semanaHasta}, la ventana vigente del resumen por grupos (los siete dias que terminan ayer); con un
     * viernes, esa semana. La ficha de una persona abre en la misma ventana que se esta mirando.
     */
    @Override
    @Transactional(readOnly = true)
    public GrupoDelLider grupoDe(ConsultaGrupoDelLider consulta) {
        acceso.requireLiderazgoActivo(consulta.actorId());
        Instant ahora = clock.now();
        GrupoAcompanado grupo = personas.requireGrupoConMentor(consulta.grupoId(), ahora);
        // Hoy en la zona del GRUPO (regla 02): rechaza un dia que no es viernes y una semana que todavia no llego. La
        // tabla recibe lo que se pidio (null = la vigente), igual que el resumen: la misma lectura del semaforo.
        VentanaDeLaFicha ventana = VentanaDeLaFicha.de(consulta.semanaHasta(), hoyDelGrupo(grupo, ahora));
        TablaDeGrupo tabla = tablaDelSemaforo.tablaDe(grupo.grupoId(), consulta.semanaHasta());
        List<PersonaDeLaLista> lista = armarLista(grupo, tabla, ventana);
        // `cerrada` es de la VENTANA, no de cada persona (E-629): la tabla del semaforo solo la da por cerrada si TODOS
        // tienen la foto del cierre, y quien empezo despues de esa semana no la tiene, asi que un grupo con un
        // integrante nuevo mostraba «todavia sin cerrar» en una semana que ya paso. Se toma del calendario, igual que
        // la ficha de la persona.
        return new GrupoDelLider(tabla.grupoId(), tabla.grupoNombre(), tabla.desde(), tabla.hasta(), ventana.cerrada(),
                ConteoDeLaLista.de(lista), lista);
    }

    private List<PersonaDeLaLista> armarLista(GrupoAcompanado grupo, TablaDeGrupo tabla, VentanaDeLaFicha ventana) {
        List<PersonaDeLaLista> lista = new ArrayList<>(delSemaforo(tabla));
        personas.acompananteConPrograma(grupo)
                .filter(acompanante -> lista.stream().noneMatch(p -> p.personaId().equals(acompanante.id().value())))
                .map(acompanante -> sinColor(acompanante, ventana))
                .ifPresent(lista::add);
        lista.sort(OrdenDeLaListaDelLider.orden());
        return lista;
    }

    /** Los aprendices de la tabla, sin las cuentas de prueba, con el rol de su cuenta. */
    private List<PersonaDeLaLista> delSemaforo(TablaDeGrupo tabla) {
        List<UserId> ids = tabla.filas().stream().map(fila -> UserId.of(fila.personaId())).toList();
        Set<UserId> deLaPrueba = personas.dePrueba(ids);
        Map<UserId, UserSummary> perfiles = usuarios.findByIds(ids);
        return tabla.filas().stream()
                .filter(fila -> !deLaPrueba.contains(UserId.of(fila.personaId())))
                .map(fila -> aPersona(fila, perfiles.get(UserId.of(fila.personaId()))))
                .toList();
    }

    private static PersonaDeLaLista aPersona(FilaDeLaTabla fila, UserSummary perfil) {
        return new PersonaDeLaLista(fila.personaId(), fila.nombre(), fila.avatarUrl(),
                perfil == null ? null : perfil.role(), ColorDeLaFila.de(fila.color()), fila.porcentaje(),
                fila.diasConDatos(), fila.dias(), fila.motivo());
    }

    /**
     * Quien lleva su programa en el grupo (su mentor o su guía): el semáforo del grupo mide aprendices, así que sale
     * con su porcentaje y «No se mide en el semáforo», sin barras de colores que lo contradigan.
     */
    private PersonaDeLaLista sinColor(UserSummary acompanante, VentanaDeLaFicha ventana) {
        VentanaDelSemaforo suya = semaforoDeLaPersona.ventanaDe(acompanante.id(), ventana).orElse(null);
        return new PersonaDeLaLista(acompanante.id().value(), acompanante.fullName(), acompanante.avatarUrl(),
                acompanante.role(), ColorDeLaFila.NO_SE_MIDE, suya == null ? null : suya.porcentaje(),
                suya == null ? 0 : suya.diasConDatos(), List.of(), null);
    }

    private static LocalDate hoyDelGrupo(GrupoAcompanado grupo, Instant ahora) {
        return ahora.atZone(ZoneId.of(grupo.zonaHoraria())).toLocalDate();
    }
}
