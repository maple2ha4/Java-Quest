package dev.javaquest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:quest;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
 "quest.judge-enabled=true","spring.datasource.username=sa","spring.datasource.password=","quest.judge-token=test-token-with-at-least-32-characters"})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired Accounts accounts;
    static final AtomicBoolean judgePass=new AtomicBoolean(false);
    static HttpServer judge;
    static volatile int judgeMode=0;
    static Cookie session;
    static {
        try {
            judge=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            judge.createContext("/judge",exchange->{
                // Contract test double only. Real sandbox coverage is in scripts/smoke.py.
                exchange.getRequestBody().readAllBytes();
                String body="{\"passed\":"+judgePass.get()+",\"status\":\""+(judgePass.get()?"PASSED":"WRONG_ANSWER")+"\",\"tests\":[{\"passed\":"+judgePass.get()+",\"status\":\""+(judgePass.get()?"PASSED":"WRONG_ANSWER")+"\"}],\"totalTests\":1}";
                byte[] data=body.getBytes(StandardCharsets.UTF_8);
                if(judgeMode==2) data="{\"passed\":true,\"status\":\"PASSED\",\"tests\":[],\"totalTests\":1}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(judgeMode==1?503:200,data.length);
                exchange.getResponseBody().write(data);exchange.close();
            });judge.start();
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("quest.judge-url",()->"http://127.0.0.1:"+judge.getAddress().getPort());}
    @AfterAll static void stop(){judge.stop(0);}
    @Test @Order(1) void registerAndProtectRequests() throws Exception {
        mvc.perform(get("/api/capabilities")).andExpect(jsonPath("$.judgeEnabled").value(true));
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{}" )).andExpect(status().isForbidden());
        var response=mvc.perform(post("/api/auth/register").header("X-Quest-Client","1").contentType("application/json")
            .content("{\"email\":\"test@example.com\",\"name\":\"学习者\",\"password\":\"test-password\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.completed").isEmpty()).andReturn().getResponse();
        String cookie=response.getHeader("Set-Cookie");assertTrue(cookie.contains("HttpOnly"));assertTrue(cookie.contains("SameSite=Strict"));
        session=new Cookie("quest_session",cookie.split(";",2)[0].split("=",2)[1]);
    }
    @Test @Order(2) void curriculumHidesTestsAndEnforcesUnlocks() throws Exception {
        mvc.perform(get("/api/lessons")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(42))).andExpect(jsonPath("$[0].tests").doesNotExist());
        mvc.perform(post("/api/submissions").cookie(session).header("X-Quest-Client","1").contentType("application/json").content(submission("variables","Main.java")))
            .andExpect(status().isForbidden());
    }
    @Test @Order(3) void invalidPathsRejectedAndDraftsPersist() throws Exception {
        mvc.perform(put("/api/drafts/hello-world").cookie(session).header("X-Quest-Client","1").contentType("application/json")
            .content("{\"files\":[{\"name\":\"../Main.java\",\"content\":\"test\"}]}" )).andExpect(status().isBadRequest());
        mvc.perform(put("/api/drafts/hello-world").cookie(session).header("X-Quest-Client","1").contentType("application/json")
            .content("{\"files\":[{\"name\":\"Main.java\",\"content\":\"saved draft\"}]}" )).andExpect(status().isOk());
        mvc.perform(get("/api/drafts/hello-world").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$[0].content").value("saved draft"));
        mvc.perform(put("/api/notes/hello-world").cookie(session).header("X-Quest-Client","1").contentType("application/json").content("{\"body\":\"Remember the semicolon\"}" )).andExpect(status().isOk());
        mvc.perform(get("/api/notes").cookie(session)).andExpect(jsonPath("$[0].body").value("Remember the semicolon"));
    }
    @Test @Order(4) void failedSubmissionNeverCompletes() throws Exception {
        accounts.limit("unused",1,1);Thread.sleep(2050);judgePass.set(false);
        mvc.perform(post("/api/submissions").cookie(session).header("X-Quest-Client","1").contentType("application/json").content(submission("hello-world","Main.java")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.profile.xp").value(0)).andExpect(jsonPath("$.newlyCompleted").value(false));
    }
    @Test @Order(5) void passAwardsOnceAndSurvivesLogin() throws Exception {
        Thread.sleep(2050);judgePass.set(true);
        mvc.perform(post("/api/submissions").cookie(session).header("X-Quest-Client","1").contentType("application/json").content(submission("hello-world","Main.java")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.awardedXp").value(30)).andExpect(jsonPath("$.profile.completed[0]").value("hello-world"));
        Thread.sleep(2050);
        mvc.perform(post("/api/submissions").cookie(session).header("X-Quest-Client","1").contentType("application/json").content(submission("hello-world","Main.java")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.awardedXp").value(0)).andExpect(jsonPath("$.profile.xp").value(30));
        mvc.perform(post("/api/auth/logout").cookie(session).header("X-Quest-Client","1")).andExpect(status().isOk());
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
        var result=mvc.perform(post("/api/auth/login").header("X-Quest-Client","1").contentType("application/json")
            .content("{\"email\":\"test@example.com\",\"password\":\"test-password\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.xp").value(30)).andReturn();
        assertNotNull(result.getResponse().getHeader("Set-Cookie"));
    }
    @Test @Order(6) void privateDataIsIsolated() throws Exception {
        var result=mvc.perform(post("/api/auth/register").header("X-Quest-Client","1").contentType("application/json")
            .content("{\"email\":\"another@example.com\",\"name\":\"另一个人\",\"password\":\"test-password\"}"))
            .andExpect(status().isOk()).andReturn();
        Cookie other=new Cookie("quest_session",result.getResponse().getHeader("Set-Cookie").split(";",2)[0].split("=",2)[1]);
        mvc.perform(get("/api/submissions").cookie(other)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/notes").cookie(other)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/drafts/hello-world").cookie(other)).andExpect(jsonPath("$[0].content").value(org.hamcrest.Matchers.containsString("Hello, learner!")));
    }
    @Test @Order(7) void judgeFailureAndIncompleteSuccessDoNotAwardProgress() throws Exception {
        var login=mvc.perform(post("/api/auth/login").header("X-Quest-Client","1").contentType("application/json")
            .content("{\"email\":\"test@example.com\",\"password\":\"test-password\"}"))
            .andExpect(status().isOk()).andReturn();
        Cookie current=new Cookie("quest_session",login.getResponse().getHeader("Set-Cookie").split(";",2)[0].split("=",2)[1]);
        try {
            for(int mode:new int[]{1,2}) {
                judgeMode=mode; Thread.sleep(2050);
                mvc.perform(post("/api/submissions").cookie(current).header("X-Quest-Client","1").contentType("application/json").content(submission("variables","Main.java")))
                    .andExpect(status().isServiceUnavailable());
                mvc.perform(get("/api/me").cookie(current)).andExpect(jsonPath("$.xp").value(30)).andExpect(jsonPath("$.completed.length()").value(1));
            }
        } finally { judgeMode=0; }
    }
    @Test @Order(8) void projectDraftsPreserveReadOnlyDriverAndPrerequisites() throws Exception {
        var registration=mvc.perform(post("/api/auth/register").header("X-Quest-Client","1").contentType("application/json")
            .content("{\"email\":\"project@example.com\",\"name\":\"工程练习者\",\"password\":\"project-test-password\"}"))
            .andExpect(status().isOk()).andReturn().getResponse();
        Cookie current=new Cookie("quest_session",registration.getHeader("Set-Cookie").split(";",2)[0].split("=",2)[1]);
        var draft=mvc.perform(get("/api/drafts/order-subtotal").cookie(current)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var files=mapper.readTree(draft);
        mvc.perform(put("/api/drafts/order-subtotal").cookie(current).header("X-Quest-Client","1").contentType("application/json").content(mapper.writeValueAsString(Map.of("files",files))))
            .andExpect(status().isOk());
        mvc.perform(post("/api/submissions").cookie(current).header("X-Quest-Client","1").contentType("application/json").content(mapper.writeValueAsString(Map.of("lessonId","order-subtotal","files",files))))
            .andExpect(status().isForbidden());
        ((com.fasterxml.jackson.databind.node.ObjectNode)files.get(0)).put("content","public class Forged {}");
        mvc.perform(put("/api/drafts/order-subtotal").cookie(current).header("X-Quest-Client","1").contentType("application/json").content(mapper.writeValueAsString(Map.of("files",files))))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/curriculum")).andExpect(status().isOk()).andExpect(jsonPath("$[0].chapters[0].topics[0].exercises[0].lessonId").value("hello-world"));
    }
    private String submission(String lesson,String name) throws Exception {
        return mapper.writeValueAsString(Map.of("lessonId",lesson,"files",List.of(Map.of("name",name,"content","public class Main {}"))));
    }
}
