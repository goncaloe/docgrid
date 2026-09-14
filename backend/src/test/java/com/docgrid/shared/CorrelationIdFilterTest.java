package com.docgrid.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.docgrid.support.PostgresContainerConfiguration;

/**
 * O id de correlação de cada pedido HTTP: aceitado do cliente quando é um id seguro,
 * gerado quando não vem nada, e sempre devolvido no header. O MDC tem de ficar limpo
 * depois do pedido — um id de um pedido não pode vazar para o seguinte no mesmo fio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresContainerConfiguration.class)
class CorrelationIdFilterTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void answerGeneratesAnIdWhenTheRequestDoesNotBringOne() throws Exception {
        var result =
                mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andReturn();

        String header = result.getResponse().getHeader(Correlation.HEADER);
        assertThat(header).matches(UUID_PATTERN);
    }

    @Test
    void answerEchoesACleanClientId() throws Exception {
        var result = mvc.perform(get("/actuator/health").header(Correlation.HEADER, "pedido-abc_123"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(Correlation.HEADER)).isEqualTo("pedido-abc_123");
    }

    @Test
    void answerReplacesJunkWithANewId() throws Exception {
        var result = mvc.perform(get("/actuator/health").header(Correlation.HEADER, "lixo </script> & tudo"))
                .andExpect(status().isOk())
                .andReturn();

        // O lixo não pode entrar nos logs; quem o enviou recebe um id novo, limpo.
        assertThat(result.getResponse().getHeader(Correlation.HEADER)).matches(UUID_PATTERN);
        assertThat(result.getResponse().getHeader(Correlation.HEADER)).isNotEqualTo("lixo </script> & tudo");
    }

    @Test
    void mdcIsCleanAfterTheRequest() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());

        // O mesmo fio serviu o pedido; se o finally não limpar, o id fica preso aqui.
        assertThat(Correlation.current()).isNull();
    }

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
}
