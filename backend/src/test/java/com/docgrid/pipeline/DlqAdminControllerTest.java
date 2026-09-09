package com.docgrid.pipeline;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.docgrid.pipeline.dto.DlqMessageView;
import com.docgrid.pipeline.dto.RedriveSummary;

/**
 * O contrato REST da administração da DLQ. O {@link DlqAdmin} está mockado — a mecânica
 * do SQS prova-se em {@link DlqRedriveTest}; aqui prova-se só que os endpoints existem,
 * respondem e serializam o que devem.
 */
@WebMvcTest(DlqAdminController.class)
class DlqAdminControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DlqAdmin dlqAdmin;

    @Test
    void listsWhatIsParkedOnTheDlq() throws Exception {
        when(dlqAdmin.list())
                .thenReturn(
                        List.of(new DlqMessageView("m-1", "docgrid-documents", "org/abc/2026/09/fatura.pdf", 4, null)));

        mvc.perform(get("/api/admin/dlq"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].messageId").value("m-1"))
                .andExpect(jsonPath("$[0].storageKey").value("org/abc/2026/09/fatura.pdf"))
                .andExpect(jsonPath("$[0].approximateReceiveCount").value(4));
    }

    @Test
    void redrivesTheWholeDlq() throws Exception {
        when(dlqAdmin.redriveAll()).thenReturn(new RedriveSummary(3));

        mvc.perform(post("/api/admin/dlq/redrive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movedToMainQueue").value(3));

        verify(dlqAdmin).redriveAll();
    }
}
