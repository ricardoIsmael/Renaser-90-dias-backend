package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.application.ports.in.CerrarHuecosDeLaSemanaUseCase.ResultadoDelCierre;
import com.renaser.os.mentoring.application.ports.out.FotosDeHuecosPort;
import com.renaser.os.mentoring.domain.model.huecos.FotoDeHuecos;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El barrido que guarda la foto de la semana que acaba de terminar (spec 015, FR-018): idempotente, aislado por
 * persona, sin transaccion global y en la zona de cada una. Los relojes caen en la madrugada UTC.
 */
class CierreDeHuecosServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final ZoneId TOKIO = ZoneId.of("Asia/Tokyo");
    /** Lunes 26 de octubre de 2026, 05:45 UTC: el lunes 00:45 en Lima. La semana del 19 al 25 acaba de terminar. */
    private static final FixedClock LUNES_EN_LIMA = FixedClock.at(Instant.parse("2026-10-26T05:45:00Z"));
    private static final LocalDate SEMANA_TERMINADA = LocalDate.of(2026, 10, 19);

    private final ProgramasActivadosFinder programas = mock(ProgramasActivadosFinder.class);
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final ParticipacionProgramaFinder participaciones = mock(ParticipacionProgramaFinder.class);
    private final CalculoDeLosHuecos calculo = mock(CalculoDeLosHuecos.class);
    private final FotosDeHuecosPort fotos = mock(FotosDeHuecosPort.class);

    private final Map<UserId, UserSummary> cuentas = new LinkedHashMap<>();
    private final List<ProgramaActivado> padron = new ArrayList<>();
    private final Set<UserId> conFoto = new HashSet<>();

    @BeforeEach
    void preparar() {
        when(programas.pagina(eq(0), anyInt())).thenReturn(padron);
        when(usuarios.findByIds(any())).thenAnswer(invocacion -> {
            Collection<UserId> pedidos = invocacion.getArgument(0);
            Map<UserId, UserSummary> encontradas = new LinkedHashMap<>();
            pedidos.forEach(id -> {
                if (cuentas.containsKey(id)) {
                    encontradas.put(id, cuentas.get(id));
                }
            });
            return encontradas;
        });
        when(fotos.conFotoDe(any(), any())).thenAnswer(invocacion -> {
            Collection<UserId> pedidos = invocacion.getArgument(0);
            Set<UserId> yaTienen = new HashSet<>(pedidos);
            yaTienen.retainAll(conFoto);
            return yaTienen;
        });
        when(fotos.registrar(any())).thenReturn(true);
        when(calculo.calcular(any(), any(), any())).thenAnswer(invocacion ->
                SemanasDeHuecosDePrueba.delContrato(invocacion.getArgument(1)));
    }

    private CierreDeHuecosService servicio(FixedClock reloj) {
        return new CierreDeHuecosService(programas, usuarios, participaciones, calculo, fotos, reloj);
    }

    private UserId persona(ZoneId zona, UserStatus estado, boolean dePrueba, LocalDate primeraFecha) {
        UserId id = UserId.of(UUID.randomUUID());
        cuentas.put(id, new UserSummary(id, "Persona", null, UserRole.TRAINEE, estado, null, dePrueba));
        padron.add(new ProgramaActivado(id, zona, primeraFecha, primeraFecha.plusDays(89)));
        when(participaciones.deParticipante(id)).thenReturn(Optional.of(new ParticipacionPrograma(id, true, 20,
                primeraFecha, zona, FasePrograma.PHASE_2_DEVELOPMENT, null, null, UserRole.TRAINEE,
                estado != UserStatus.ACTIVE, true)));
        return id;
    }

    private UserId activa() {
        return persona(LIMA, UserStatus.ACTIVE, false, LocalDate.of(2026, 9, 28));
    }

    private List<FotoDeHuecos> fotosGuardadas() {
        ArgumentCaptor<FotoDeHuecos> captor = ArgumentCaptor.forClass(FotoDeHuecos.class);
        verify(fotos, atLeast(0)).registrar(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("el lunes 00:45 en Lima guarda la foto de la semana del 19 al 25, a nombre de cada persona y en su zona")
    void guardaLaSemanaQueTermino() {
        UserId ana = activa();
        UserId beto = activa();

        ResultadoDelCierre resultado = servicio(LUNES_EN_LIMA).cerrarPendientes();

        assertThat(resultado).isEqualTo(new ResultadoDelCierre(2, 2, 0));
        assertThat(fotosGuardadas()).extracting(FotoDeHuecos::participante).containsExactly(ana, beto);
        assertThat(fotosGuardadas()).allSatisfy(foto -> {
            assertThat(foto.semana().lunes()).isEqualTo(SEMANA_TERMINADA);
            assertThat(foto.zona()).isEqualTo(LIMA);
            assertThat(foto.cerradaEn()).isEqualTo(LUNES_EN_LIMA.now());
        });
        // Se calcula con el «hoy» de la persona: el lunes 26 de Lima.
        verify(calculo, times(2)).calcular(any(), eq(SEMANA_TERMINADA), eq(LocalDate.of(2026, 10, 26)));
    }

    @Test
    @DisplayName("el domingo 22:00 en Lima (ya lunes en UTC) la semana del 19 al 25 todavia no termino: se cierra la anterior")
    void antesDeQueTermineElDomingoDeLima() {
        FixedClock domingoEnLima = FixedClock.at(Instant.parse("2026-10-26T03:00:00Z"));
        activa();

        servicio(domingoEnLima).cerrarPendientes();

        assertThat(fotosGuardadas()).hasSize(1);
        assertThat(fotosGuardadas().get(0).semana().lunes()).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    @Test
    @DisplayName("cada persona cierra cuando termina SU domingo: a las 05:45 UTC del lunes en Tokio ya es lunes 14:45, y en Lima 00:45")
    void cadaZona() {
        // 2026-10-25T20:00Z: en Tokio ya es lunes 26 05:00 (la semana del 19 al 25 termino); en Lima es domingo 15:00.
        FixedClock domingoTarde = FixedClock.at(Instant.parse("2026-10-25T20:00:00Z"));
        UserId lima = persona(LIMA, UserStatus.ACTIVE, false, LocalDate.of(2026, 9, 28));
        UserId tokio = persona(TOKIO, UserStatus.ACTIVE, false, LocalDate.of(2026, 9, 28));

        servicio(domingoTarde).cerrarPendientes();

        assertThat(fotosGuardadas()).extracting(foto -> foto.participante().equals(tokio) ? "tokio" : "lima",
                foto -> foto.semana().lunes()).containsExactlyInAnyOrder(
                tuple("tokio", SEMANA_TERMINADA),
                tuple("lima", LocalDate.of(2026, 10, 12)));
        assertThat(lima).isNotEqualTo(tokio);
    }

    @Test
    @DisplayName("idempotente: quien ya tiene la foto no se vuelve a calcular ni a guardar")
    void idempotente() {
        UserId ana = activa();
        UserId beto = activa();
        conFoto.add(ana);

        ResultadoDelCierre primera = servicio(LUNES_EN_LIMA).cerrarPendientes();

        assertThat(primera).isEqualTo(new ResultadoDelCierre(2, 1, 0));
        verify(fotos, times(1)).registrar(any());
        assertThat(fotosGuardadas().get(0).participante()).isEqualTo(beto);
        verify(calculo, times(1)).calcular(any(), any(), any());
    }

    @Test
    @DisplayName("idempotente tambien si otra instancia guardo primero: registrar dice false y no cuenta como guardada")
    void otraInstanciaGanoLaCarrera() {
        activa();
        when(fotos.registrar(any())).thenReturn(false);

        ResultadoDelCierre resultado = servicio(LUNES_EN_LIMA).cerrarPendientes();

        assertThat(resultado).isEqualTo(new ResultadoDelCierre(1, 0, 0));
    }

    @Test
    @DisplayName("una persona que falla no frena a las demas y se cuenta como fallida")
    void aisladoPorPersona() {
        UserId ana = activa();
        UserId beto = activa();
        UserId carla = activa();
        ParticipacionPrograma deBeto = participacionDe(beto);
        doThrow(new IllegalStateException("se cayo el calendario")).when(calculo).calcular(eq(deBeto), any(), any());

        ResultadoDelCierre resultado = servicio(LUNES_EN_LIMA).cerrarPendientes();

        assertThat(resultado).isEqualTo(new ResultadoDelCierre(3, 2, 1));
        assertThat(fotosGuardadas()).extracting(FotoDeHuecos::participante).containsExactly(ana, carla);
    }

    @Test
    @DisplayName("sin foto de una persona que fallo, la proxima corrida la reintenta")
    void sePonerAlDia() {
        UserId beto = activa();
        ParticipacionPrograma deBeto = participacionDe(beto);
        doThrow(new IllegalStateException("fallo")).when(calculo).calcular(eq(deBeto), any(), any());
        CierreDeHuecosService servicio = servicio(LUNES_EN_LIMA);

        assertThat(servicio.cerrarPendientes()).isEqualTo(new ResultadoDelCierre(1, 0, 1));

        reset(calculo);
        when(calculo.calcular(any(), any(), any())).thenAnswer(invocacion ->
                SemanasDeHuecosDePrueba.delContrato(invocacion.getArgument(1)));
        assertThat(servicio.cerrarPendientes()).isEqualTo(new ResultadoDelCierre(1, 1, 0));
    }

    @Test
    @DisplayName("no se miden las cuentas suspendidas ni las de prueba")
    void soloActivasYReales() {
        persona(LIMA, UserStatus.SUSPENDED, false, LocalDate.of(2026, 9, 28));
        persona(LIMA, UserStatus.ACTIVE, true, LocalDate.of(2026, 9, 28));

        ResultadoDelCierre resultado = servicio(LUNES_EN_LIMA).cerrarPendientes();

        assertThat(resultado).isEqualTo(ResultadoDelCierre.VACIO);
        verify(fotos, never()).registrar(any());
    }

    @Test
    @DisplayName("una semana que no toca el programa de la persona (empezo despues) no se guarda")
    void semanaFueraDelPrograma() {
        persona(LIMA, UserStatus.ACTIVE, false, LocalDate.of(2026, 10, 27));

        ResultadoDelCierre resultado = servicio(LUNES_EN_LIMA).cerrarPendientes();

        assertThat(resultado).isEqualTo(ResultadoDelCierre.VACIO);
        verify(fotos, never()).registrar(any());
    }

    @Test
    @DisplayName("la semana de su Dia 1 si se guarda aunque el programa empiece a mitad de semana")
    void semanaDelDiaUno() {
        persona(LIMA, UserStatus.ACTIVE, false, LocalDate.of(2026, 10, 22));

        ResultadoDelCierre resultado = servicio(LUNES_EN_LIMA).cerrarPendientes();

        assertThat(resultado).isEqualTo(new ResultadoDelCierre(1, 1, 0));
    }

    @Test
    @DisplayName("recorre el padron por paginas")
    void pagina() {
        List<ProgramaActivado> primera = new ArrayList<>();
        for (int i = 0; i < CierreDeHuecosService.TAMANO_LOTE; i++) {
            primera.add(new ProgramaActivado(UserId.of(UUID.randomUUID()), LIMA, LocalDate.of(2026, 10, 27),
                    LocalDate.of(2027, 1, 24)));
        }
        when(programas.pagina(0, CierreDeHuecosService.TAMANO_LOTE)).thenReturn(primera);
        when(programas.pagina(CierreDeHuecosService.TAMANO_LOTE, CierreDeHuecosService.TAMANO_LOTE))
                .thenReturn(List.of());

        servicio(LUNES_EN_LIMA).cerrarPendientes();

        verify(programas).pagina(0, CierreDeHuecosService.TAMANO_LOTE);
        verify(programas).pagina(CierreDeHuecosService.TAMANO_LOTE, CierreDeHuecosService.TAMANO_LOTE);
    }

    /** La participacion que el servicio le pasa al calculo para esa persona. */
    private ParticipacionPrograma participacionDe(UserId id) {
        return participaciones.deParticipante(id).orElseThrow();
    }
}
