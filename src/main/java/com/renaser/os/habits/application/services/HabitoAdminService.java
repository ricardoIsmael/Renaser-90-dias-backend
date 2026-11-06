package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.habitoadmin.ActualizarHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.CambiarActivoHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.ConsultarCatalogoAdminUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.ConsultarRolesElegiblesUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.CrearHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.EliminarHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.HabitoDelCatalogo;
import com.renaser.os.habits.application.ports.out.audiencia.ContarRegistrosDeHabitoPort;
import com.renaser.os.habits.application.ports.out.categoria.LoadCategoriaHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.SaveHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.SaveHorarioHabitoPort;
import com.renaser.os.habits.domain.model.audiencia.Audiencia;
import com.renaser.os.habits.domain.model.audiencia.CodigoDeRol;
import com.renaser.os.habits.domain.model.categoria.CategoriaHabito;
import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Panel admin de catalogo (hueco #11): alta, edicion, activar/desactivar y borrado de habitos SISTEMA.
 *
 * <p>Spec 001 (D-264): cada habito lleva su audiencia por rol. Lo que toca a la audiencia (validar, versionar,
 * presentar) vive en {@link AudienciasDelCatalogo}; este servicio solo decide CUANDO.
 *
 * <p>E-659 (issue #44): el alta crea tambien el horario del habito, en la misma transaccion, como
 * {@link MisHabitosService#crear} lo hace con uno personal. Sin una fila en {@code horarios_habito}
 * {@code RegistroService} no genera el registro de nadie y el habito, aunque este activo y tenga audiencia, no le
 * aparece a ninguna persona.
 */
@Service
public class HabitoAdminService implements ConsultarCatalogoAdminUseCase, CrearHabitoUseCase, ActualizarHabitoUseCase,
        CambiarActivoHabitoUseCase, EliminarHabitoUseCase, ConsultarRolesElegiblesUseCase {

    static final String CON_HISTORIA = "Este hábito ya tiene registros de personas. Desactívalo en lugar de eliminarlo.";

    private final LoadHabitoPort loadPort;
    private final SaveHabitoPort savePort;
    private final SaveHorarioHabitoPort saveHorarioPort;
    private final HabitoAdminGuard guard;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final AudienciasDelCatalogo audiencias;
    private final ContarRegistrosDeHabitoPort registrosPort;
    private final LoadCategoriaHabitoPort categoriasPort;

    public HabitoAdminService(LoadHabitoPort loadPort, SaveHabitoPort savePort, SaveHorarioHabitoPort saveHorarioPort,
                               HabitoAdminGuard guard, Clock clock, IdGenerator idGenerator,
                               AudienciasDelCatalogo audiencias, ContarRegistrosDeHabitoPort registrosPort,
                               LoadCategoriaHabitoPort categoriasPort) {
        this.categoriasPort = categoriasPort;
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.saveHorarioPort = saveHorarioPort;
        this.guard = guard;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.audiencias = audiencias;
        this.registrosPort = registrosPort;
    }

    @Override
    public List<HabitoDelCatalogo> listar(UserId actorId) {
        return listar(actorId, null);
    }

    @Override
    public List<HabitoDelCatalogo> listar(UserId actorId, CodigoDeRol rol) {
        return listar(actorId, rol, Formacion.POR_DEFECTO);
    }

    @Override
    public List<HabitoDelCatalogo> listar(UserId actorId, CodigoDeRol rol, Formacion formacion) {
        guard.requireAdmin(actorId);
        if (rol != null) {
            audiencias.validar(Audiencia.de(Set.of(rol)));
        }
        List<Habito> delCatalogo = loadPort.catalogoCompleto().stream()
                .filter(h -> formacion == null || h.esDeFormacion(formacion)).toList();
        List<HabitoDelCatalogo> catalogo = audiencias.presentar(delCatalogo);
        return rol == null ? catalogo : catalogo.stream().filter(h -> h.audienciaGuardada().aplicaA(rol)).toList();
    }

    @Override
    public List<RolElegible> rolesElegibles(UserId actorId) {
        guard.requireAdmin(actorId);
        return audiencias.rolesElegibles();
    }

    @Override
    @Transactional
    public HabitoDelCatalogo crear(CrearHabitoCommand command) {
        guard.requireAdmin(command.actorId());
        audiencias.validar(command.audiencia());
        exigirCategoriaActivaDe(command.formacion(), command.detalles().categoriaClave());
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD 5.4.7).
        HabitoId id = HabitoId.of(idGenerator.newId());
        // Se arma ANTES de guardar nada: una hora fuera del dia (despues de las 23:40) se rechaza sin escribir.
        HorarioHabito horario = HorarioHabito.todoElPrograma(HorarioHabitoId.of(idGenerator.newId()), id,
                command.horaDisparo(), clock.now());
        Habito habito = savePort.save(Habito.crearDeSistema(id, command.formacion(), command.titulo(), command.tipo(),
                command.detalles(), clock.now()));
        saveHorarioPort.save(horario);
        audiencias.guardarAlCrear(habito.id(), command.audiencia(), command.actorId());
        return audiencias.presentar(habito);
    }

    @Override
    @Transactional
    public HabitoDelCatalogo actualizar(ActualizarHabitoCommand command) {
        guard.requireAdmin(command.actorId());
        Habito habito = requireHabito(command.habitoId());
        Audiencia audiencia = command.audiencia();
        if (audiencia != null) {
            audiencias.validar(audiencia);
        }
        DetallesHabito detalles = detallesPara(command, habito);
        if (!detalles.categoriaClave().equals(habito.categoriaClave())) {
            // Spec 023: mover un hábito a otra categoría solo se puede dentro de SU formación (nunca a otra: para eso se copia).
            exigirCategoriaActivaDe(habito.formacion(), detalles.categoriaClave());
        }
        habito.actualizarDetalles(detalles, clock.now());
        Habito guardado = savePort.save(habito);
        if (audiencia != null) {
            audiencias.guardarSiCambio(guardado.id(), audiencia, command.actorId());
        }
        return audiencias.presentar(guardado);
    }

    /** Un pedido que no informó la bandera de intoxicación la deja como estaba (E-263). */
    private static DetallesHabito detallesPara(ActualizarHabitoCommand command, Habito habito) {
        return command.conservaObligatorioEnIntoxicacion()
                ? command.detalles().conObligatorioEnIntoxicacion(habito.obligatorioEnIntoxicacion())
                : command.detalles();
    }

    @Override
    @Transactional
    public HabitoDelCatalogo cambiarActivo(CambiarActivoHabitoCommand command) {
        guard.requireAdmin(command.actorId());
        Habito habito = requireHabito(command.habitoId());
        if (command.activo()) {
            habito.activar(clock.now());
        } else {
            habito.desactivar(clock.now());
        }
        return audiencias.presentar(savePort.save(habito));
    }

    /**
     * Con historia no se borra (FR-020): se avisa ANTES de tocar la base, con un motivo que la pantalla puede mostrar.
     * Antes llegaba a la FK RESTRICT de {@code registros_habito} y volvía un 409 genérico.
     */
    @Override
    @Transactional
    public void eliminar(EliminarHabitoCommand command) {
        guard.requireAdmin(command.actorId());
        requireHabito(command.habitoId());
        if (registrosPort.tieneRegistros(command.habitoId())) {
            throw new IllegalStateException(CON_HISTORIA);
        }
        savePort.eliminar(command.habitoId());
    }

    /**
     * Spec 023, FR-002: un hábito se crea y se mueve solo a una categoría ACTIVA de SU misma formación. La llave compuesta de la base
     * también lo impone; esto lo dice con un motivo que la pantalla puede mostrar.
     */
    private void exigirCategoriaActivaDe(Formacion formacion, String clave) {
        CategoriaHabito categoria = categoriasPort.byClave(clave)
                .orElseThrow(() -> new IllegalArgumentException("Esa categoría no existe."));
        if (categoria.formacion() != formacion) {
            throw new IllegalArgumentException("Esa categoría no es de la formación de este hábito.");
        }
        if (!categoria.activa()) {
            throw new IllegalArgumentException("Esa categoría está desactivada. Actívala o elige otra.");
        }
    }

    /** Un habito PERSONAL no es del catalogo: para este panel no existe (contrato admin-habits, 404). */
    private Habito requireHabito(HabitoId id) {
        return loadPort.byId(id).filter(Habito::esDeSistema)
                .orElseThrow(() -> new NoSuchElementException("Hábito no encontrado: " + id));
    }
}
