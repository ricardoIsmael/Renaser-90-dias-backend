package com.renaser.os.users.domain.model.mentorprofile;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MentorProfileTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Test
    void newMentorStartsAtN0Green() {
        MentorProfile profile = MentorProfile.create(UserId.of(UUID.randomUUID()), CLOCK);

        assertThat(profile.level()).isEqualTo(MentorLevel.N0);
        assertThat(profile.operationalStatus()).isEqualTo(MentorOperationalStatus.GREEN);
        assertThat(profile.bio()).isNull();
    }

    @Test
    void promoteChangesLevelAndTimestamp() {
        MentorProfile profile = MentorProfile.create(UserId.of(UUID.randomUUID()), CLOCK);
        FixedClock later = FixedClock.at(CLOCK.now().plusSeconds(60));

        profile.promoteTo(MentorLevel.N2, later);

        assertThat(profile.level()).isEqualTo(MentorLevel.N2);
        assertThat(profile.updatedAt()).isEqualTo(later.now());
    }

    @Test
    void operationalStatusCanTurnYellowOrRed() {
        MentorProfile profile = MentorProfile.create(UserId.of(UUID.randomUUID()), CLOCK);

        profile.changeOperationalStatus(MentorOperationalStatus.RED, CLOCK);

        assertThat(profile.operationalStatus()).isEqualTo(MentorOperationalStatus.RED);
    }
}
