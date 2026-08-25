package com.renaser.os.users.infrastructure.adapter.out.persistence.mentorprofile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataMentorProfileRepository extends JpaRepository<MentorProfileJpaEntity, UUID> {
}
