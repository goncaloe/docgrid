package com.docgrid.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.docgrid.support.RepositoryTest;

/** O semeador de demonstração corre a cada arranque: correr duas vezes não duplica nada. */
@RepositoryTest
class DevBootstrapTest {

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private UserRepository users;

    @Test
    void seedsTheDemoUserOnceAndExposesItsIds() {
        DevBootstrap bootstrap = new DevBootstrap(organizations, users);

        bootstrap.run(null);
        bootstrap.run(null);

        assertThat(users.findAll()).hasSize(1);
        assertThat(organizations.findAll()).hasSize(1);

        User demo = users.findByEmailIgnoreCase(DevBootstrap.DEMO_EMAIL).orElseThrow();
        assertThat(bootstrap.currentUserId()).isEqualTo(demo.getId());
        assertThat(bootstrap.currentOrganizationId()).isEqualTo(demo.getOrganizationId());
    }
}
