package com.docgrid.auth;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface OrganizationRepository extends JpaRepository<Organization, UUID> {}
