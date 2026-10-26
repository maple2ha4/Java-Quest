package dev.javaquest;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import java.util.*;
import java.util.concurrent.Semaphore;

@Service
public class Learning {
    private final JdbcTemplate db;
    private final Curriculum curriculum;
    private final ObjectMapper mapper;
    private final RestClient judge;
    private final Semaphore capacity=new Semaphore(2);
    public Learning(JdbcTemplate db,Curriculum curriculum,ObjectMapper mapper,
            @Value("${quest.judge-url}") String url,@Value("${quest.judge-token}") String token) {
        this.db=db; this.curriculum=curriculum; this.mapper=mapper;
        if(token.length()<32) throw new IllegalArgumentException("JUDGE_TOKEN must contain at least 32 characters");
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); factory.setReadTimeout(140000);
        judge=RestClient.builder().baseUrl(url).requestFactory(factory).defaultHeader("X-Judge-Token",token).build();
    }
    public Map<String,Object> me(long user) {
        var profile=new HashMap<>(db.queryForMap("SELECT id,email,display_name AS name,created_at AS createdAt FROM app_user WHERE id=?",user));
        var complete=db.queryForList("SELECT lesson_id FROM progress WHERE user_id=? ORDER BY completed_at",String.class,user);
        profile.put("completed",complete);
        profile.put("xp",curriculum.all().stream().filter(l->complete.contains(l.id())).mapToInt(Curriculum.Lesson::xp).sum());
        profile.put("submissions",db.queryForObject("SELECT COUNT(*) FROM submission WHERE user_id=?",Integer.class,user));
        return profile;
    }
    public void validateFiles(List<Curriculum.SourceFile> files) {
        if(files==null || files.isEmpty() || files.size()>8) throw new ApiError(400,"请提供 1–8 个 Java 文件");
        Set<String> names=new HashSet<>(); int length=0;
        for(var f:files) {
            if(f==null || f.name()==null || !f.name().matches("[A-Za-z][A-Za-z0-9_]{0,60}\\.java") || f.content()==null || !names.add(f.name()))
                throw new ApiError(400,"文件名必须为不重复的 Java 类名，例如 Main.java");
            length+=f.content().length();
        }
        if(!names.contains("Main.java") || length>32768) throw new ApiError(400,"必须包含 Main.java，总代码不超过 32KiB 字符");
    }
    public JsonNode run(long user,String lessonId,List<Curriculum.SourceFile> files) {
        var lesson=curriculum.get(lessonId); validateFiles(files);
        if(lesson.order()>1) {
            var previous=curriculum.all().get(lesson.order()-2);
            if(db.queryForObject("SELECT COUNT(*) FROM progress WHERE user_id=? AND lesson_id=?",Integer.class,user,previous.id())==0)
                throw new ApiError(403,"请先完成上一关："+previous.title());
        }
        if(!capacity.tryAcquire()) throw new ApiError(429,"判题服务繁忙，请稍后重试");
        try {
            byte[] payload=mapper.writeValueAsBytes(Map.of("files",files,"tests",lesson.tests()));
            JsonNode result=judge.post().uri("/judge").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .contentLength(payload.length).body(payload).retrieve().body(JsonNode.class);
            if(result==null || !result.path("passed").isBoolean() || !result.path("tests").isArray()
                || result.path("totalTests").asInt()!=lesson.tests().size() || result.path("tests").isEmpty()
                || result.path("tests").size()>lesson.tests().size()) throw new IllegalStateException("Invalid judge response");
            if(result.get("passed").asBoolean()) {
                if(!"PASSED".equals(result.path("status").asText()) || result.get("tests").size()!=lesson.tests().size())
                    throw new IllegalStateException("Incomplete judge response");
                for(var test:result.get("tests")) if(!test.path("passed").asBoolean() || !"PASSED".equals(test.path("status").asText()))
                    throw new IllegalStateException("Inconsistent judge response");
            }
            return result;
        } catch(Exception e) { throw new ApiError(503,"隔离判题服务暂不可用，请检查 Docker、镜像和判题服务；本次没有通关"); }
        finally { capacity.release(); }
    }
    @Transactional public Map<String,Object> saveResult(long user,String lessonId,List<Curriculum.SourceFile> files,JsonNode result) {
        // Serialize progress writes for this learner; repeat submissions cannot award XP twice.
        db.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE",Long.class,user);
        db.update("INSERT INTO submission(user_id,lesson_id,files_json,result_json,status) VALUES(?,?,?,?,?)",
                user,lessonId,json(files),json(result),result.get("status").asText());
        boolean newlyCompleted=false;
        if(result.get("passed").asBoolean() && db.queryForObject("SELECT COUNT(*) FROM progress WHERE user_id=? AND lesson_id=?",Integer.class,user,lessonId)==0) {
            db.update("INSERT INTO progress(user_id,lesson_id) VALUES(?,?)",user,lessonId); newlyCompleted=true;
        }
        return Map.of("result",result,"newlyCompleted",newlyCompleted,"awardedXp",newlyCompleted?curriculum.get(lessonId).xp():0,"profile",me(user));
    }
    public List<Map<String,Object>> submissions(long user) {
        var rows=db.queryForList("SELECT id,lesson_id AS lessonId,status,created_at AS createdAt,result_json FROM submission WHERE user_id=? ORDER BY id DESC LIMIT 50",user);
        return rows.stream().map(r->{var copy=new HashMap<>(r); copy.put("result",parse((String)copy.remove("result_json")));return (Map<String,Object>)copy;}).toList();
    }
    public JsonNode draft(long user,String lesson) {
        curriculum.get(lesson);
        var rows=db.queryForList("SELECT files_json FROM draft WHERE user_id=? AND lesson_id=?",String.class,user,lesson);
        return rows.isEmpty()?mapper.valueToTree(curriculum.get(lesson).starter()):parse(rows.getFirst());
    }
    @Transactional public void draft(long user,String lesson,List<Curriculum.SourceFile> files) {
        curriculum.get(lesson); validateFiles(files);
        db.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE",Long.class,user);
        db.update("DELETE FROM draft WHERE user_id=? AND lesson_id=?",user,lesson);
        db.update("INSERT INTO draft(user_id,lesson_id,files_json) VALUES(?,?,?)",user,lesson,json(files));
    }
    public List<Map<String,Object>> notes(long user) { return db.queryForList("SELECT lesson_id AS lessonId,body,updated_at AS updatedAt FROM note WHERE user_id=? ORDER BY updated_at DESC",user); }
    @Transactional public void note(long user,String lesson,String body) {
        curriculum.get(lesson);
        db.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE",Long.class,user);
        db.update("DELETE FROM note WHERE user_id=? AND lesson_id=?",user,lesson);
        if(!body.isBlank()) db.update("INSERT INTO note(user_id,lesson_id,body) VALUES(?,?,?)",user,lesson,body);
    }
    private String json(Object value) { try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);} }
    private JsonNode parse(String value) { try{return mapper.readTree(value);}catch(Exception e){throw new IllegalStateException(e);} }
}
