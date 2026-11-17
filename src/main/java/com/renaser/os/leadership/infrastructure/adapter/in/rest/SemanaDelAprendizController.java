package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.DiaDeLaFicha;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.SemanaDeLaPersona;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDelAprendizDelMentorUseCase;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDelAprendizDelMentorUseCase.ConsultaSemanaDelAprendiz;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDelAprendizDelMentorUseCase.SemanaDelAprendizParaElMentor;
import com.renaser.os.mentoring.api.ObligacionDelDia;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/v1/mentor/groups/{groupId}/learners/{userId}/semana?semanaHasta=YYYY-MM-DD} (spec 055): la semana del
 * aprendiz de sábado a viernes —la del semáforo—, día por día y con el pilar de cada hábito, para la ficha del mentor.
 *
 * <p>El permiso declarado ({@code USE_APP}) no protege nada: MENTOR pasa el interceptor sin que se le mire nada (A-1). Lo que
 * protege es el guard del servicio: acompañar el grupo HOY con la cuenta activa y que el aprendiz sea de ese grupo; si no, 403
 * {@code NO_ACOMPANA}. Datos de una persona: nada se guarda en caché ({@code no-store}).
 */
@RestController
@RequestMapping("/api/v1/mentor/groups/{groupId}/learners/{userId}/semana")
@Funcionalidad("mentor.mi_grupo")
public class SemanaDelAprendizController {

    private final ConsultarSemanaDelAprendizDelMentorUseCase consultarSemana;

    public SemanaDelAprendizController(ConsultarSemanaDelAprendizDelMentorUseCase consultarSemana) {
        this.consultarSemana = consultarSemana;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "AccesoDelMentorAlAprendiz: acompanante VIGENTE del grupo con cuenta activa Y el aprendiz del grupo")
    @GetMapping
    public ResponseEntity<SemanaDelAprendizResponse> semana(@ActorAutenticado UserId actorId,
                                                            @PathVariable UUID groupId,
                                                            @PathVariable UUID userId,
                                                            @RequestParam(required = false)
                                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                            LocalDate semanaHasta) {
        // semanaHasta null = la semana en curso; cuál es «hoy» lo decide el servicio, en la zona del aprendiz.
        SemanaDelAprendizParaElMentor semana = consultarSemana.semanaDe(
                new ConsultaSemanaDelAprendiz(actorId, groupId, userId, semanaHasta));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SemanaDelAprendizResponse.from(semana));
    }

    /**
     * @param diaUno        el Día 1 de su programa (limita el mes); null si todavía no lo eligió
     * @param primeraSemana el sábado que abre la semana del Día 1; null si todavía no lo eligió
     * @param color         VERDE, AMARILLO, ROJO o SIN_DATOS: el de la semana
     */
    public record SemanaDelAprendizResponse(UUID aprendizId, String nombre, String avatarUrl, String zona,
                                            LocalDate desde, LocalDate hasta, LocalDate diaUno,
                                            LocalDate primeraSemana, boolean semanaEnCurso, boolean cerrada,
                                            String color, BigDecimal porcentaje, List<DiaResponse> dias) {

        static SemanaDelAprendizResponse from(SemanaDelAprendizParaElMentor lectura) {
            SemanaDeLaPersona semana = lectura.semana();
            return new SemanaDelAprendizResponse(semana.personaId(), semana.nombre(), semana.avatarUrl(), semana.zona(),
                    semana.desde(), semana.hasta(), lectura.diaUno(), semana.primeraSemana(), !semana.cerrada(),
                    semana.cerrada(), semana.color().name(), semana.porcentaje(),
                    semana.dias().stream().map(DiaResponse::from).toList());
        }
    }

    /** @param motivo por qué el día no tiene nada que medir (en palabras, nunca como incumplimiento); null si se midió */
    public record DiaResponse(LocalDate fecha, Integer diaDePrograma, String motivo,
                              List<ObligacionDelDia> obligaciones) {

        static DiaResponse from(DiaDeLaFicha dia) {
            return new DiaResponse(dia.fecha(), dia.diaDePrograma(), dia.motivo(), dia.obligaciones());
        }
    }
}
