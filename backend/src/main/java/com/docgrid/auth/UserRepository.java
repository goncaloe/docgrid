package com.docgrid.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface UserRepository extends JpaRepository<User, UUID> {

    /** A autenticação da etapa 06 encontra a pessoa pelo email, sem saber a organização. */
    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByOrganizationIdAndDeactivatedAtIsNull(UUID organizationId);
}
