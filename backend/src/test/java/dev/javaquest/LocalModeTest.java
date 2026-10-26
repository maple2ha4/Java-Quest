package dev.javaquest;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"quest.judge-enabled=false","quest.judge-token="})
@ActiveProfiles("local")
@AutoConfigureMockMvc
class LocalModeTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    static final Path dataDir;
    static {
        try { dataDir=Files.createTempDirectory("quest-local-test-"); }
        catch(Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("quest.data-dir",()->dataDir.toString());
    }
    @Test void startsWithFileDatabaseAndNoJudgeToken() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/capabilities")).andExpect(jsonPath("$.judgeEnabled").value(false));
        assertTrue(Files.isRegularFile(dataDir.resolve("javaquest.mv.db")));
        try(var connection=db.getDataSource().getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:file:"));
        }
    }
    @Test void savesLearningDataButNeverAwardsUnjudgedProgress() throws Exception {
        var registration=mvc.perform(post("/api/auth/register").header("X-Quest-Client","1").contentType("application/json")
            .content("{\"email\":\"local@example.com\",\"name\":\"本地学习者\",\"password\":\"local-test-password\"}"))
            .andExpect(status().isOk()).andReturn().getResponse();
        Cookie session=new Cookie("quest_session",registration.getHeader("Set-Cookie").split(";",2)[0].split("=",2)[1]);
        mvc.perform(put("/api/drafts/hello-world").cookie(session).header("X-Quest-Client","1").contentType("application/json")
            .content("{\"files\":[{\"name\":\"Main.java\",\"content\":\"saved local draft\"}]}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/notes/hello-world").cookie(session).header("X-Quest-Client","1").contentType("application/json")
            .content("{\"body\":\"Local note\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/submissions").cookie(session).header("X-Quest-Client","1").contentType("application/json")
            .content("{\"lessonId\":\"hello-world\",\"files\":[{\"name\":\"Main.java\",\"content\":\"public class Main {}\"}]}"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("未启用")));
        mvc.perform(get("/api/me").cookie(session)).andExpect(jsonPath("$.xp").value(0))
            .andExpect(jsonPath("$.completed").isEmpty()).andExpect(jsonPath("$.submissions").value(0));
        mvc.perform(get("/api/drafts/hello-world").cookie(session)).andExpect(jsonPath("$[0].content").value("saved local draft"));
        mvc.perform(get("/api/notes").cookie(session)).andExpect(jsonPath("$[0].body").value("Local note"));
    }
}
