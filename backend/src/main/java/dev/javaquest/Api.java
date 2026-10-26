package dev.javaquest;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class Api {
    private final Accounts accounts; private final Learning learning; private final Curriculum curriculum; private final JdbcTemplate db;
    public Api(Accounts accounts,Learning learning,Curriculum curriculum,JdbcTemplate db) {
        this.accounts=accounts;this.learning=learning;this.curriculum=curriculum;this.db=db;
    }
    public record Registration(@NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(min=2,max=40) String name, @NotBlank @Size(min=8,max=72) String password) {}
    public record Login(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(max=72) String password) {}
    public record Submission(@NotBlank @Size(max=60) String lessonId,@NotNull List<Curriculum.SourceFile> files) {}
    public record Draft(@NotNull List<Curriculum.SourceFile> files) {}
    public record Note(@NotNull @Size(max=10000) String body) {}
    @GetMapping("/health") public Object health() { db.queryForObject("SELECT 1",Integer.class);return Map.of("status","ok"); }
    @GetMapping("/capabilities") public Object capabilities() { return Map.of("judgeEnabled",learning.judgingEnabled(),"judgeMode",learning.judgingMode()); }
    @GetMapping("/lessons") public Object lessons() { return curriculum.all().stream().map(Curriculum.Lesson::publicView).toList(); }
    @PostMapping("/auth/register") public Object register(@Valid @RequestBody Registration body,HttpServletRequest req,HttpServletResponse res) {
        accounts.limit("auth:"+req.getRemoteAddr(),10,60000);
        return learning.me(accounts.register(body.email(),body.name(),body.password(),res));
    }
    @PostMapping("/auth/login") public Object login(@Valid @RequestBody Login body,HttpServletRequest req,HttpServletResponse res) {
        accounts.limit("auth:"+req.getRemoteAddr(),10,60000);
        return learning.me(accounts.login(body.email(),body.password(),res));
    }
    @PostMapping("/auth/logout") public Object logout(HttpServletRequest req,HttpServletResponse res) { accounts.logout(req,res);return Map.of("ok",true); }
    @GetMapping("/me") public Object me(HttpServletRequest req) { return learning.me(accounts.require(req)); }
    @PostMapping("/submissions") public Object submit(@Valid @RequestBody Submission body,HttpServletRequest req) {
        long user=accounts.require(req); accounts.limit("run:"+user,1,2000);
        var result=learning.run(user,body.lessonId(),body.files());
        return learning.saveResult(user,body.lessonId(),body.files(),result);
    }
    @GetMapping("/submissions") public Object submissions(HttpServletRequest req) { return learning.submissions(accounts.require(req)); }
    @GetMapping("/drafts/{id}") public Object draft(@PathVariable String id,HttpServletRequest req) {return learning.draft(accounts.require(req),id);}
    @PutMapping("/drafts/{id}") public Object saveDraft(@PathVariable String id,@Valid @RequestBody Draft body,HttpServletRequest req) {
        learning.draft(accounts.require(req),id,body.files());return Map.of("ok",true);
    }
    @GetMapping("/notes") public Object notes(HttpServletRequest req) {return learning.notes(accounts.require(req));}
    @PutMapping("/notes/{id}") public Object note(@PathVariable String id,@Valid @RequestBody Note body,HttpServletRequest req) {
        learning.note(accounts.require(req),id,body.body());return Map.of("ok",true);
    }
}
