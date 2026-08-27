package com.renaser.os.users.infrastructure.adapter.in.rest.user;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.user.GetLogrosUseCase;
import com.renaser.os.users.application.ports.in.user.GetLogrosUseCase.GetLogrosQuery;
import com.renaser.os.users.application.ports.in.user.GetLogrosUseCase.Logros;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LogrosController.class)
@AutoConfigureMockMvc(addFilters = false)
class LogrosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetLogrosUseCase getLogrosUseCase;

    @Test
    void logrosDevuelveLosNombresDeCampoQueEsperaElMovil() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        Instant primerHabito = Instant.parse("2026-01-10T00:00:00Z");
        Logros logros = new Logros(42, null, 10L, primerHabito, 20, null, 5, 30L, null);
        when(getLogrosUseCase.getLogros(new GetLogrosQuery(actorId))).thenReturn(logros);

        mockMvc.perform(get("/api/v1/profile/logros").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programDay").value(42))
                .andExpect(jsonPath("$.streak").doesNotExist())
                .andExpect(jsonPath("$.totalHabitsCompleted").value(10))
                .andExpect(jsonPath("$.firstHabitCompletedAt").value(primerHabito.toString()))
                .andExpect(jsonPath("$.totalRocksCompleted").value(20))
                .andExpect(jsonPath("$.firstRockCompletedAt").doesNotExist())
                .andExpect(jsonPath("$.bestRocksStreakDays").value(5))
                .andExpect(jsonPath("$.radarEntriesCount").value(30))
                .andExpect(jsonPath("$.firstRadarEntryAt").doesNotExist());
    }

    @Test
    void logrosComoSuspendidoDevuelve403() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(getLogrosUseCase.getLogros(new GetLogrosQuery(actorId)))
                .thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        mockMvc.perform(get("/api/v1/profile/logros").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isForbidden());
    }

    /** Mismo caso que el backend viejo: staff sin `participantes_programa` recibe 404,
     * no un objeto con ceros fabricados. */
    @Test
    void logrosSinParticipacionEnElProgramaDevuelve404() throws Exception {
        UserId actorId = UserId.of(UUID.randomUUID());
        when(getLogrosUseCase.getLogros(new GetLogrosQuery(actorId)))
                .thenThrow(new NoSuchElementException("El actor no esta inscripto en el programa: " + actorId));

        mockMvc.perform(get("/api/v1/profile/logros").header("X-Actor-Id", actorId.toString()))
                .andExpect(status().isNotFound());
    }
}
