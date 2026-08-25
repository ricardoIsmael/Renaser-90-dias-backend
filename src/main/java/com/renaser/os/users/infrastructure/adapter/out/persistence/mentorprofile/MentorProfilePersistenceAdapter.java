package com.renaser.os.users.infrastructure.adapter.out.persistence.mentorprofile;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.mentorprofile.LoadMentorProfilePort;
import com.renaser.os.users.application.ports.out.mentorprofile.SaveMentorProfilePort;
import com.renaser.os.users.domain.model.mentorprofile.MentorProfile;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class MentorProfilePersistenceAdapter implements LoadMentorProfilePort, SaveMentorProfilePort {

    private final SpringDataMentorProfileRepository repository;
    private final MentorProfilePersistenceMapper mapper;

    MentorProfilePersistenceAdapter(SpringDataMentorProfileRepository repository,
                                     MentorProfilePersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<MentorProfile> byUserId(UserId userId) {
        return repository.findById(userId.value()).map(mapper::toDomain);
    }

    @Override
    public MentorProfile save(MentorProfile mentorProfile) {
        var saved = repository.save(mapper.toEntity(mentorProfile));
        return mapper.toDomain(saved);
    }
}
