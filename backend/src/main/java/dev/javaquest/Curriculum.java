package dev.javaquest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class Curriculum {
    public record SourceFile(String name, String content) {}
    public record TestCase(String input, String expected) {}
    public record Lesson(String id, int order, String title, String shortTitle, String summary,
                         String concept, int xp, int minutes, List<String> paragraphs,
                         String task, List<String> hints, List<SourceFile> starter, List<TestCase> tests) {
        public Map<String,Object> publicView() {
            return Map.ofEntries(Map.entry("id",id), Map.entry("order",order), Map.entry("title",title),
                Map.entry("shortTitle",shortTitle), Map.entry("summary",summary), Map.entry("concept",concept),
                Map.entry("xp",xp), Map.entry("minutes",minutes), Map.entry("paragraphs",paragraphs),
                Map.entry("task",task), Map.entry("hints",hints), Map.entry("starter",starter));
        }
    }
    private final ObjectMapper mapper;
    private List<Lesson> lessons;
    public Curriculum(ObjectMapper mapper) { this.mapper=mapper; }
    @PostConstruct void load() throws Exception {
        try(var input = new ClassPathResource("lessons.json").getInputStream()) {
            lessons = mapper.readValue(input,new TypeReference<>(){});
        }
    }
    public List<Lesson> all() { return lessons; }
    public Lesson get(String id) { return lessons.stream().filter(l->l.id().equals(id)).findFirst()
        .orElseThrow(()->new ApiError(404,"关卡不存在")); }
}
