package com.docgrid.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.docgrid.support.PostgresContainerConfiguration;

/**
 * O contrato HTTP da autenticação — os únicos endpoints que um pedido sem token alcança.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresContainerConfiguration.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void registerIsReachableWithoutAToken() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationName": "Padaria do Bairro, Lda.",
                                  "organizationTaxId": "501442889",
                                  "adminEmail": "ana.%s@padaria.pt",
                                  "adminPassword": "uma-password-forte",
                                  "adminFullName": "Ana Ribeiro"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void registeringTheSameEmailTwiceReturnsAProblemDetailConflict() throws Exception {
        String email = "duplicado.%s@padaria.pt".formatted(UUID.randomUUID());
        String body = """
                {
                  "organizationName": "Padaria do Bairro, Lda.",
                  "organizationTaxId": "501442889",
                  "adminEmail": "%s",
                  "adminPassword": "uma-password-forte",
                  "adminFullName": "Ana Ribeiro"
                }
                """.formatted(email);

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void loginWithBadCredentialsReturnsAProblemDetailUnauthorized() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "ninguem@padaria.pt", "password": "qualquer"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    void aMalformedRegistrationBodyReturnsAProblemDetailBadRequest() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationName": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }
}
