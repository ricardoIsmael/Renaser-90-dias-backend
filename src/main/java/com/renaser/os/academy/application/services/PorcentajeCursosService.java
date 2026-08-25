package com.renaser.os.academy.application.services;

import com.renaser.os.points.api.PorcentajeCursosFinder;
import com.renaser.os.academy.application.ports.out.curso.LoadCursoPort;
import com.renaser.os.academy.application.ports.out.curso.LoadLeccionPort;
import com.renaser.os.academy.application.ports.out.progreso.LoadProgresoLeccionPort;
import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.progreso.PorcentajeCursos;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.ParticipacionProgramaFinder.UsuarioConDiaPrograma;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementacion real de {@link PorcentajeCursosFinder} (D-43): exactamente
 * CUATRO consultas en total, sin importar cuantos participantes se pidan —
 * nunca una por aprendiz. Ese es el criterio de aceptacion del encargo: si
 * esto alguna vez pasa a iterar {@code participantes} llamando a un puerto
 * por cabeza, se reprodujo el incidente documentado en
 * `prisma/migrations/general_ranking_scores_function.sql` (RenaserBack,
 * "Too many database connections opened" con ~30 cuentas activas).
 *
 * <ol>
 *   <li>{@link ParticipacionProgramaFinder#usuariosActivosConDiaPrograma} — dia
 *       de programa de TODOS los TRAINEE activos (se filtra en memoria a los
 *       {@code participantes} pedidos; ya es una sola consulta para todo el
 *       padron, no una por persona).</li>
 *   <li>{@link LoadCursoPort#listarTodos()} — catalogo completo.</li>
 *   <li>{@link LoadLeccionPort#contarTotalPorCurso()} — total de lecciones por curso.</li>
 *   <li>{@link LoadProgresoLeccionPort#completadasPorCursoEnLote} — completadas por
 *       (usuario, curso) para TODOS los solicitados de una vez.</li>
 * </ol>
 *
 * <p>El calculo en si (regla "sin cursos accesibles → 100.0", escala 1) es
 * DOMINIO PURO ({@link PorcentajeCursos}) — esta clase solo trae los crudos,
 * arma el conjunto de cursos accesibles por participante reusando
 * {@link Curso#visibleEnCatalogoPara} (el MISMO gate que el catalogo del
 * aprendiz, AC-01) y orquesta.
 */
@Service
public class PorcentajeCursosService implements PorcentajeCursosFinder {

    private final ParticipacionProgramaFinder participacionFinder;
    private final LoadCursoPort loadCursoPort;
    private final LoadLeccionPort loadLeccionPort;
    private final LoadProgresoLeccionPort loadProgresoLeccionPort;

    public PorcentajeCursosService(ParticipacionProgramaFinder participacionFinder, LoadCursoPort loadCursoPort,
                                    LoadLeccionPort loadLeccionPort,
                                    LoadProgresoLeccionPort loadProgresoLeccionPort) {
        this.participacionFinder = participacionFinder;
        this.loadCursoPort = loadCursoPort;
        this.loadLeccionPort = loadLeccionPort;
        this.loadProgresoLeccionPort = loadProgresoLeccionPort;
    }

    @Override
    public Map<UserId, BigDecimal> porcentajePorParticipante(Collection<UserId> participantes) {
        if (participantes == null || participantes.isEmpty()) {
            return Map.of();
        }

        Map<UserId, Integer> diaProgramaPorTrainee = diaProgramaDeTraineesActivos(Set.copyOf(participantes));
        if (diaProgramaPorTrainee.isEmpty()) {
            return Map.of();
        }

        List<Curso> catalogo = loadCursoPort.listarTodos();
        Map<CursoId, Integer> totalLeccionesPorCurso = loadLeccionPort.contarTotalPorCurso();
        Map<UserId, Map<CursoId, Integer>> completadasPorUsuario =
                loadProgresoLeccionPort.completadasPorCursoEnLote(diaProgramaPorTrainee.keySet());

        Map<UserId, BigDecimal> resultado = new LinkedHashMap<>();
        for (Map.Entry<UserId, Integer> entry : diaProgramaPorTrainee.entrySet()) {
            UserId usuarioId = entry.getKey();
            Set<CursoId> accesibles = cursosAccesiblesPara(catalogo, entry.getValue());
            int total = sumarPorCursosAccesibles(accesibles, totalLeccionesPorCurso);
            int completadas = sumarPorCursosAccesibles(accesibles,
                    completadasPorUsuario.getOrDefault(usuarioId, Map.of()));
            resultado.put(usuarioId, PorcentajeCursos.calcular(total, completadas));
        }
        return resultado;
    }

    /** Una sola consulta para TODO el padron de TRAINEE activos, filtrada en memoria — nunca una por persona. */
    private Map<UserId, Integer> diaProgramaDeTraineesActivos(Set<UserId> solicitados) {
        Map<UserId, Integer> resultado = new LinkedHashMap<>();
        for (UsuarioConDiaPrograma candidato : participacionFinder.usuariosActivosConDiaPrograma(Set.of(UserRole.TRAINEE))) {
            if (solicitados.contains(candidato.id())) {
                resultado.put(candidato.id(), candidato.diaPrograma());
            }
        }
        return resultado;
    }

    /** Mismo gate que el catalogo del aprendiz (AC-01) — rol TRAINEE fijo, espejo de {@code sumarProgresoCursos}. */
    private static Set<CursoId> cursosAccesiblesPara(List<Curso> catalogo, Integer diaPrograma) {
        return catalogo.stream()
                .filter(curso -> curso.visibleEnCatalogoPara(UserRole.TRAINEE, diaPrograma))
                .map(Curso::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static int sumarPorCursosAccesibles(Set<CursoId> accesibles, Map<CursoId, Integer> valoresPorCurso) {
        int suma = 0;
        for (CursoId cursoId : accesibles) {
            Integer valor = valoresPorCurso.get(cursoId);
            if (valor != null) {
                suma += valor;
            }
        }
        return suma;
    }
}
