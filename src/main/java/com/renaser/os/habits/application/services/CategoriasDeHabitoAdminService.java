package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.categoria.CategoriaDelCatalogo;
import com.renaser.os.habits.application.ports.in.categoria.ConsultarCategoriasAdminUseCase;
import com.renaser.os.habits.application.ports.in.categoria.GestionarCategoriasUseCase;
import com.renaser.os.habits.application.ports.out.categoria.LoadCategoriaHabitoPort;
import com.renaser.os.habits.application.ports.out.categoria.LoadCategoriaHabitoPort.ConteoDeHabitos;
import com.renaser.os.habits.application.ports.out.categoria.RegistrarCambioDelCatalogoPort;
import com.renaser.os.habits.application.ports.out.categoria.RegistrarCambioDelCatalogoPort.Accion;
import com.renaser.os.habits.application.ports.out.categoria.RegistrarCambioDelCatalogoPort.CambioDelCatalogo;
import com.renaser.os.habits.application.ports.out.categoria.RegistrarCambioDelCatalogoPort.Entidad;
import com.renaser.os.habits.application.ports.out.categoria.SaveCategoriaHabitoPort;
import com.renaser.os.habits.domain.model.categoria.CategoriaHabito;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Las categorias de habito de cada formacion, para Administracion (spec 023, historia 2). Reutiliza {@link HabitoAdminGuard} (solo
 * ADMIN/ALCHEMIST con la cuenta activa) y deja constancia de cada cambio (FR-024).
 *
 * <p>Las reglas que cruzan filas viven aqui y no en el agregado: el nombre es unico dentro de la formacion (sin tildes ni mayusculas) y una
 * categoria con habitos activos no se desactiva ni se elimina; con historial (habitos de cualquier estado) se desactiva pero no se elimina.
 */
@Service
public class CategoriasDeHabitoAdminService implements ConsultarCategoriasAdminUseCase, GestionarCategoriasUseCase {

    static final String CON_HABITOS_ACTIVOS = "Esta categoría tiene hábitos activos. Muévelos o desactívalos primero.";
    static final String CON_HISTORIAL = "Esta categoría tiene hábitos con historial. Desactívala en lugar de eliminarla.";
    static final String NOMBRE_REPETIDO = "Ya hay una categoría con ese nombre en esta formación.";
    private static final int INTENTOS_DE_CLAVE = 50;

    private final LoadCategoriaHabitoPort loadPort;
    private final SaveCategoriaHabitoPort savePort;
    private final RegistrarCambioDelCatalogoPort auditoria;
    private final HabitoAdminGuard guard;
    private final Clock clock;

    public CategoriasDeHabitoAdminService(LoadCategoriaHabitoPort loadPort, SaveCategoriaHabitoPort savePort,
                                           RegistrarCambioDelCatalogoPort auditoria, HabitoAdminGuard guard, Clock clock) {
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.auditoria = auditoria;
        this.guard = guard;
        this.clock = clock;
    }

    @Override
    public List<CategoriaDelCatalogo> listar(UserId actorId, Formacion formacion) {
        guard.requireAdmin(actorId);
        List<Formacion> formaciones = formacion == null ? List.of(Formacion.values()) : List.of(formacion);
        return formaciones.stream().flatMap(f -> loadPort.deFormacion(f).stream()).map(this::conConteo).toList();
    }

    @Override
    @Transactional
    public CategoriaDelCatalogo crear(CrearCategoriaCommand command) {
        guard.requireAdmin(command.actorId());
        Formacion formacion = command.formacion();
        if (loadPort.existeNombreEn(formacion, command.nombre(), null)) {
            throw new IllegalStateException(NOMBRE_REPETIDO);
        }
        CategoriaHabito categoria = CategoriaHabito.crear(claveLibre(formacion, command.nombre()), formacion, command.nombre(),
                loadPort.siguienteOrden(formacion), command.icono(), clock.now());
        savePort.save(categoria);
        registrar(formacion, categoria.clave(), Accion.CREADA, command.actorId(), Map.of("nombre", categoria.nombre()));
        return conConteo(categoria);
    }

    @Override
    @Transactional
    public CategoriaDelCatalogo actualizar(ActualizarCategoriaCommand command) {
        guard.requireAdmin(command.actorId());
        CategoriaHabito categoria = requireCategoria(command.clave());
        if (command.nombre() != null && !command.nombre().trim().equals(categoria.nombre())) {
            renombrar(categoria, command);
        }
        if (command.cambiaElIcono()) {
            String antes = categoria.icono();
            categoria.cambiarIcono(command.icono(), clock.now());
            registrar(categoria.formacion(), categoria.clave(), Accion.ICONO, command.actorId(), cambio(antes, categoria.icono()));
        }
        savePort.save(categoria);
        return conConteo(categoria);
    }

    private void renombrar(CategoriaHabito categoria, ActualizarCategoriaCommand command) {
        if (loadPort.existeNombreEn(categoria.formacion(), command.nombre(), categoria.clave())) {
            throw new IllegalStateException(NOMBRE_REPETIDO);
        }
        String antes = categoria.nombre();
        categoria.renombrar(command.nombre(), clock.now());
        registrar(categoria.formacion(), categoria.clave(), Accion.RENOMBRADA, command.actorId(), cambio(antes, categoria.nombre()));
    }

    @Override
    @Transactional
    public CategoriaDelCatalogo cambiarActiva(CambiarActivaCommand command) {
        guard.requireAdmin(command.actorId());
        CategoriaHabito categoria = requireCategoria(command.clave());
        if (categoria.activa() == command.activa()) {
            return conConteo(categoria);
        }
        if (command.activa()) {
            categoria.activar(clock.now());
        } else {
            exigirSinHabitosActivos(categoria.clave());
            categoria.desactivar(clock.now());
        }
        savePort.save(categoria);
        registrar(categoria.formacion(), categoria.clave(), command.activa() ? Accion.ACTIVADA : Accion.DESACTIVADA,
                command.actorId(), Map.of());
        return conConteo(categoria);
    }

    @Override
    @Transactional
    public List<CategoriaDelCatalogo> ordenar(OrdenarCategoriasCommand command) {
        guard.requireAdmin(command.actorId());
        List<CategoriaHabito> actuales = loadPort.deFormacion(command.formacion());
        exigirLasMismasClaves(actuales, command.claves());
        Map<String, CategoriaHabito> porClave = new LinkedHashMap<>();
        actuales.forEach(c -> porClave.put(c.clave(), c));
        List<CategoriaDelCatalogo> resultado = new ArrayList<>();
        int orden = 1;
        for (String clave : command.claves()) {
            CategoriaHabito categoria = porClave.get(clave);
            if (categoria.orden() != orden) {
                categoria.mover(orden, clock.now());
                savePort.save(categoria);
            }
            resultado.add(conConteo(categoria));
            orden++;
        }
        registrar(command.formacion(), "*", Accion.ORDENADA, command.actorId(), Map.of("orden", String.join(",", command.claves())));
        return resultado;
    }

    @Override
    @Transactional
    public void eliminar(EliminarCategoriaCommand command) {
        guard.requireAdmin(command.actorId());
        CategoriaHabito categoria = requireCategoria(command.clave());
        ConteoDeHabitos conteo = loadPort.contarHabitos(categoria.clave());
        if (conteo.activos() > 0) {
            throw new IllegalStateException(CON_HABITOS_ACTIVOS);
        }
        if (conteo.total() > 0) {
            throw new IllegalStateException(CON_HISTORIAL);
        }
        savePort.eliminar(categoria.clave());
        registrar(categoria.formacion(), categoria.clave(), Accion.ELIMINADA, command.actorId(), Map.of("nombre", categoria.nombre()));
    }

    private void exigirSinHabitosActivos(String clave) {
        if (loadPort.contarHabitos(clave).activos() > 0) {
            throw new IllegalStateException(CON_HABITOS_ACTIVOS);
        }
    }

    private static void exigirLasMismasClaves(List<CategoriaHabito> actuales, List<String> pedidas) {
        HashSet<String> esperadas = new HashSet<>();
        actuales.forEach(c -> esperadas.add(c.clave()));
        if (pedidas.size() != esperadas.size() || !esperadas.equals(new HashSet<>(pedidas))) {
            throw new IllegalArgumentException("El orden debe incluir todas las categorías de la formación, una sola vez cada una.");
        }
    }

    private String claveLibre(Formacion formacion, String nombre) {
        String base = CategoriaHabito.claveNueva(formacion, nombre);
        String clave = base;
        for (int n = 2; loadPort.existeClave(clave); n++) {
            if (n > INTENTOS_DE_CLAVE) {
                throw new IllegalStateException("No se pudo armar una clave para esta categoría.");
            }
            clave = base + "_" + n;
        }
        return clave;
    }

    private CategoriaHabito requireCategoria(String clave) {
        return loadPort.byClave(clave).orElseThrow(() -> new NoSuchElementException("Categoría no encontrada: " + clave));
    }

    private CategoriaDelCatalogo conConteo(CategoriaHabito categoria) {
        ConteoDeHabitos conteo = loadPort.contarHabitos(categoria.clave());
        return new CategoriaDelCatalogo(categoria, conteo.activos(), conteo.total());
    }

    private void registrar(Formacion formacion, String clave, Accion accion, UserId actor, Map<String, String> detalle) {
        auditoria.registrar(new CambioDelCatalogo(formacion, Entidad.CATEGORIA, clave, accion, actor, detalle, clock.now()));
    }

    private static Map<String, String> cambio(String antes, String despues) {
        Map<String, String> detalle = new LinkedHashMap<>();
        detalle.put("antes", antes == null ? "" : antes);
        detalle.put("despues", despues == null ? "" : despues);
        return detalle;
    }
}
