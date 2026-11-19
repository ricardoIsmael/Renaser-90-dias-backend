package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarDiasDeLaPersonaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarDiasDeLaPersonaUseCase.ConsultaDiasDeLaPersona;
import com.renaser.os.leadership.application.ports.in.ConsultarObjetivosDeLaPersonaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarObjetivosDeLaPersonaUseCase.ConsultaObjetivosDeLaPersona;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDeLaPersonaUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDeLaPersonaUseCase.ConsultaSemanaDeLaPersona;
import com.renaser.os.leadership.infrastructure.adapter.in.rest.SemanaDelAprendizController.SemanaDelAprendizResponse;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Las lecturas que el líder de mentores (y Administración y Alquimista) hace de una persona para la ficha que comparte con la del mentor
 * (spec 056), sea aprendiz —tenga o no grupo— o mentor que lleva su programa:
 * <ul>
 *   <li>{@code GET /api/v1/leadership/people/{personId}/semana?semanaHasta=YYYY-MM-DD} — su semana de sábado a viernes, con el pilar de
 *       cada hábito: el MISMO cuerpo que {@code GET /mentor/groups/{g}/learners/{u}/semana} (spec 055). Registra la consulta (spec 005,
 *       FR-016).</li>
 *   <li>{@code GET …/dias?desde=&hasta=} — el color de cada día, la misma forma que el {@code /dias} del mentor.</li>
 *   <li>{@code GET …/objetivos} — sus objetivos, solo para leer (FR-027).</li>
 * </ul>
 *
 * <p>El permiso declarado no alcanza solo: MENTOR pasa el interceptor sin que se le mire nada (A-1) y MENTOR_LEAD está en modo sombra.
 * Quien decide es {@code AccesoDeLiderazgo}, y la persona tiene que ser de una formación que quien pide ve (spec 022: de otra es un 404, sin
 * revelar que existe). Un mentor o una guía que no sigue su programa es 404 con el código {@code SIN_PROGRAMA}. Datos de personas: nada se guarda
 * en caché ({@code no-store}).
 */
@RestController
@RequestMapping("/api/v1/leadership/people/{personId}")
@Funcionalidad("lider.cumplimiento")
public class LecturasDeLaFichaDelLiderController {

    private static final String GUARD = "AccesoDeLiderazgo: MENTOR_LEAD/ADMIN/ALCHEMIST activo; el permiso no alcanza (A-1); "
            + "aprendiz del programa que se muestra, con o sin grupo, o mentor/guía con su programa activado; de una formación que ve quien pide";

    private final ConsultarSemanaDeLaPersonaUseCase semana;
    private final ConsultarDiasDeLaPersonaUseCase dias;
    private final ConsultarObjetivosDeLaPersonaUseCase objetivos;

    public LecturasDeLaFichaDelLiderController(ConsultarSemanaDeLaPersonaUseCase semana, ConsultarDiasDeLaPersonaUseCase dias,
                                               ConsultarObjetivosDeLaPersonaUseCase objetivos) {
        this.semana = semana;
        this.dias = dias;
        this.objetivos = objetivos;
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/semana")
    public ResponseEntity<SemanaDelAprendizResponse> semana(@ActorAutenticado UserId actorId, @PathVariable UUID personId,
                                                            @RequestParam(required = false)
                                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semanaHasta) {
        // semanaHasta null = la semana en curso; cuál es «hoy» lo decide el servicio, en la zona de la persona.
        return sinCache(SemanaDelAprendizResponse.from(semana.semanaDe(new ConsultaSemanaDeLaPersona(actorId, personId, semanaHasta))));
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/dias")
    public ResponseEntity<DiasDeLaPersonaResponse> dias(@ActorAutenticado UserId actorId, @PathVariable UUID personId,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return sinCache(DiasDeLaPersonaResponse.from(dias.diasDe(new ConsultaDiasDeLaPersona(actorId, personId, desde, hasta))));
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS, scope = GUARD)
    @GetMapping("/objetivos")
    public ResponseEntity<ObjetivosDeLaPersonaResponse> objetivos(@ActorAutenticado UserId actorId, @PathVariable UUID personId) {
        return sinCache(ObjetivosDeLaPersonaResponse.from(objetivos.objetivosDe(new ConsultaObjetivosDeLaPersona(actorId, personId))));
    }

    private static <T> ResponseEntity<T> sinCache(T cuerpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }
}
