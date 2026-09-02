package com.renaser.os.academy.application.ports.in.curso;

import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * GET /api/v1/cursos/bloqueados — los cursos que el actor vera mas adelante
 * por progresion de dia, para pintarlos con candado en la escalera del
 * catalogo. Reemplaza la RPC de Supabase {@code catalogo_cursos_bloqueados}
 * (0018), que la app llamaba directo (`src/services/cursos.ts:
 * listarCursosBloqueados`) sin equivalente REST en el backend viejo — ver
 * `docs/MODULO_ACADEMY.md` §1/§6, decision AC-15.
 *
 * <p>Lista vacia (nunca error) para roles distintos de TRAINEE — el dia de
 * programa no les aplica, asi que no hay nada que mostrar "proximo" por esa
 * razon. Solo revela el bloqueo por dia, igual que
 * {@link ConsultarMotivoBloqueoCursoUseCase}: un curso restringido por rol,
 * sin publicar o con acceso restringido NUNCA aparece aca, aunque tenga
 * `dia_desbloqueo` en el futuro — ese curso no es "proximo", simplemente no
 * es visible, y no corresponde revelarlo con candado.
 */
public interface ConsultarCursosBloqueadosUseCase {

    List<CursoBloqueado> cursosBloqueados(UserId actorId);

    /**
     * {@code portadaFirmada} es una URL de lectura ya firmada, igual que la de los cursos
     * accesibles. Antes este caso de uso devolvia solo la ruta cruda del objeto, apoyandose en que
     * el cliente movil resolvia las URLs por su cuenta ('resolverMediaUrls' del frontend
     * anterior). Ese supuesto dejo de valer cuando cambio la app: el bucket es privado, asi que
     * una ruta sin firmar no carga nunca y los cursos bloqueados se veian sin portada.
     */
    record CursoBloqueado(Curso curso, int diaDesbloqueo, int programDayActual, String portadaFirmada) {
    }
}
