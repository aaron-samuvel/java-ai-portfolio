package com.aaron.evalops;

import com.aaron.evalops.api.DocuMindGoldenEvaluationController;
import com.aaron.evalops.api.GoldenEvaluationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"evalops.parallelism=2", "evalops.queue-capacity=3"})
@AutoConfigureMockMvc
class EvaluationExecutorWiringTest {
    @Autowired MockMvc mvc;
    @Autowired
    @Qualifier("evalOpsCaseExecutor")
    Executor caseExecutor;

    @Autowired
    GoldenEvaluationController askYourDataController;

    @Autowired
    DocuMindGoldenEvaluationController docuMindController;

    @Test
    void injectsBoundedExecutorIntoBothGoldenRunners() {
        ThreadPoolTaskExecutor pool = assertInstanceOf(ThreadPoolTaskExecutor.class, caseExecutor);
        assertEquals(2, pool.getCorePoolSize());
        assertEquals(3, pool.getThreadPoolExecutor().getQueue().remainingCapacity());
        assertNotNull(askYourDataController);
        assertNotNull(docuMindController);
    }

    @Test
    void exposesEvaluationMetricsThroughActuatorWithoutPromptData() throws Exception {
        mvc.perform(post("/api/evaluations/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notice").value(org.hamcrest.Matchers.containsString("no prompt")));

        mvc.perform(get("/actuator/metrics/evalops.evaluation.runs").param("tag", "outcome:passed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").value(org.hamcrest.Matchers.greaterThan(0.0)));
        mvc.perform(get("/actuator/metrics/evalops.evaluation.duration").param("tag", "outcome:passed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").value(org.hamcrest.Matchers.greaterThan(0.0)));
    }
}
