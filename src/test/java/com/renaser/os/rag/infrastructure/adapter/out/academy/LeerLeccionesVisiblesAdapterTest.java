package com.renaser.os.rag.infrastructure.adapter.out.academy;

import com.renaser.os.academy.api.LeccionesVisiblesFinder;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unitario: el adaptador solo delega en {@link LeccionesVisiblesFinder}, sin logica propia. */
class LeerLeccionesVisiblesAdapterTest {

    @Test
    void delegaEnElFinderPublicoDeAcademy() {
        LeccionesVisiblesFinder finder = mock(LeccionesVisiblesFinder.class);
        UserId actorId = UserId.of(UUID.randomUUID());
        when(finder.leccionesVisiblesPara(actorId)).thenReturn(Set.of("l1", "l2"));
        LeerLeccionesVisiblesAdapter adapter = new LeerLeccionesVisiblesAdapter(finder);

        assertThat(adapter.visiblesParaActor(actorId)).containsExactlyInAnyOrder("l1", "l2");
    }
}
