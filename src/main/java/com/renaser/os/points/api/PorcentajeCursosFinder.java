package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * Contrato entre modulos: porcentaje de avance en cursos accesibles de cada
 * participante, EN LOTE. Consumido por `points` para el componente "cursos"
 * (15%) del Ranking General de Comunidad (50% habitos + 35% rocas + 15%
 * cursos — decision D-43, `docs/MODULOS_A_AVANZAR.md` §8).
 *
 * <p><b>Por que "en lote" no es un detalle de estilo.</b> El backend viejo
 * resolvia esto con 1 consulta a Supabase POR APRENDIZ
 * ({@code sumarProgresoCursos}, `src/features/cursos/repository.ts:824-849`
 * en RenaserBack) y con las ~30 cuentas activas del programa ya devolvia
 * "Too many database connections opened" — el incidente real documentado en
 * la cabecera de `prisma/migrations/general_ranking_scores_function.sql`
 * (RenaserBack). Cualquier implementacion que por dentro itere
 * {@code participantes} llamando a un puerto de solo-un-usuario por cabeza
 * reproduce exactamente ese bug.
 *
 * <p><b>Solo TRAINEE activo.</b> Espejo de {@code sumarProgresoCursos}, que
 * siempre evaluaba el gate de catalogo con rol {@code 'TRAINEE'} fijo, sin
 * importar el rol real del usuario — igual que el CTE {@code active_trainees}/
 * {@code cursos_pct} de la funcion SQL de arriba, que solo itera
 * {@code trainee_profiles} de usuarios {@code ACTIVE}. Un participante que no
 * sea TRAINEE activo (otro rol, TRAINEE suspendido, o TRAINEE sin fila en
 * `participantes_programa`) simplemente NO aparece en el mapa devuelto — no
 * se le inventa un {@code 100.0} por defecto; la ausencia es la senal para que
 * el caller decida que hacer. El propio Ranking General ya excluye a
 * cualquiera que no sea TRAINEE activo antes de llegar a este calculo.
 *
 * <p><b>Escala 1, no entero.</b> {@code cursos_pct} en
 * `general_ranking_scores_function.sql` redondea a 1 decimal
 * ({@code round(completadas/total*1000)/10}) y el score final pondera esos
 * tres componentes CON ese decimal antes de redondear de nuevo — bajar a
 * entero antes de ponderar da un score distinto del que hoy ve el aprendiz en
 * produccion. `rocks` expone su propio componente del mismo modo (
 * {@code BigDecimal} escala 1), asi que los tres contratos del Ranking
 * General quedan consistentes entre si.
 *
 * <p>Ver `docs/MODULO_ACADEMY.md` §5, decision AC-17.

 *
 * <p><b>Por que vive en `points` y no en el modulo que lo implementa (DIP):</b> declararlo
 * en el modulo proveedor creaba un CICLO que Spring Modulith rechaza — `habits` ya depende
 * de `points` para otorgar puntos al completar, asi que `points` no puede depender de
 * `habits` en la otra direccion. Invirtiendo la dependencia, el consumidor declara lo que
 * necesita y el proveedor lo implementa: la flecha queda en un solo sentido.
 */
public interface PorcentajeCursosFinder {

    /**
     * @param participantes usuarios a consultar (se ignoran duplicados; los que no sean
     *                       TRAINEE activo no aparecen en el resultado)
     * @return porcentaje (0.0-100.0, escala 1, {@link java.math.RoundingMode#HALF_UP}) por
     *         participante, solo para quienes calificaron
     */
    Map<UserId, BigDecimal> porcentajePorParticipante(Collection<UserId> participantes);
}
