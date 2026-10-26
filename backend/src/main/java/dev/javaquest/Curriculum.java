package dev.javaquest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.javaquest.runner.JudgeRunner;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class Curriculum {
    public record SourceFile(String name, String content) {}
    public record TestCase(String input, String expected) {}
    public record Exercise(String id,String title,String kind,String lessonId) {}
    public record Topic(String id,String title,String goal,String practiceRationale,List<Exercise> exercises) {}
    public record Chapter(String id,String title,List<Topic> topics) {}
    public record Stage(String id,String title,String goal,List<String> sampleLessonIds,List<Chapter> chapters) {}
    public record Lesson(String id, int order, String title, String shortTitle, String summary,
                         String concept, int xp, int minutes, List<String> paragraphs,
                         String task, List<String> hints, List<SourceFile> starter, List<TestCase> tests,
                         String topicId,String kind,int difficulty,List<String> prerequisites,
                         String entryClass,String buildKind,List<String> editableFiles) {
        public Map<String,Object> publicView() {
            return Map.ofEntries(Map.entry("id",id), Map.entry("order",order), Map.entry("title",title),
                Map.entry("shortTitle",shortTitle), Map.entry("summary",summary), Map.entry("concept",concept),
                Map.entry("xp",xp), Map.entry("minutes",minutes), Map.entry("paragraphs",paragraphs),
                Map.entry("task",task), Map.entry("hints",hints), Map.entry("starter",starter),
                Map.entry("topicId",topicId),Map.entry("kind",kind),Map.entry("difficulty",difficulty),
                Map.entry("prerequisites",prerequisites),Map.entry("entryClass",entryClass),
                Map.entry("buildKind",buildKind),Map.entry("editableFiles",editableFiles));
        }
    }
    private final ObjectMapper mapper;
    private List<Lesson> lessons;
    private List<Stage> stages;
    public Curriculum(ObjectMapper mapper) { this.mapper=mapper; }
    @PostConstruct void load() throws Exception {
        try(var input = new ClassPathResource("lessons.json").getInputStream()) {
            lessons = mapper.readValue(input,new TypeReference<>(){});
        }
        try(var input = new ClassPathResource("curriculum.json").getInputStream()) {
            stages = mapper.readValue(input,new TypeReference<>(){});
        }
        // Reject broken course publication, missing dependencies and cycles before serving learners.
        Set<String> ids=new HashSet<>(),orders=new HashSet<>(),topics=new HashSet<>(),refs=new HashSet<>(),planIds=new HashSet<>(),structureIds=new HashSet<>();
        for(var lesson:lessons) {
            require(ids.add(lesson.id()),"Duplicate lesson");require(orders.add(""+lesson.order()),"Duplicate order");
            require(lesson.paragraphs().size()>=2 && lesson.hints().size()>=3 && !lesson.task().isBlank(),"Incomplete teaching content");
            require(Set.of("javac","pom-check","spring-component").contains(lesson.buildKind()),"Unsupported published build kind");
            var files=lesson.starter().stream().map(f->new JudgeRunner.Source(f.name(),f.content())).toList();
            var tests=lesson.tests().stream().map(t->new JudgeRunner.TestCase(t.input(),t.expected())).toList();
            JudgeRunner.validate(new JudgeRunner.Request(files,tests,lesson.entryClass(),lesson.buildKind()));
            require(lesson.editableFiles()!=null && !lesson.editableFiles().isEmpty() &&
                lesson.editableFiles().stream().allMatch(n->lesson.starter().stream().anyMatch(f->f.name().equals(n))),"Invalid editable files");
        }
        for(var stage:stages) {
            require(structureIds.add("s:"+stage.id()),"Duplicate stage");
            for(var chapter:stage.chapters()) {
                require(structureIds.add("c:"+chapter.id()),"Duplicate chapter");
                for(var topic:chapter.topics()) {
                    require(topics.add(topic.id()) && !topic.exercises().isEmpty() && topic.practiceRationale()!=null && !topic.practiceRationale().isBlank(),"Missing progression");
                    for(var exercise:topic.exercises()) {
                        require(planIds.add(exercise.id()) && !exercise.title().isBlank(),"Duplicate/empty exercise");
                        if(exercise.lessonId()!=null) {
                            require(ids.contains(exercise.lessonId()) && refs.add(exercise.lessonId()),"Invalid published reference");
                            var lesson=get(exercise.lessonId());
                            require(lesson.topicId().equals(topic.id()) && lesson.title().equals(exercise.title()),"Course/lesson mismatch");
                        }
                    }
                }
            }
        }
        require(refs.equals(ids),"Unlisted published lessons");
        for(var stage:stages) {
            var stageTopics=stage.chapters().stream().flatMap(c->c.topics().stream()).map(Topic::id).toList();
            require(stage.sampleLessonIds()!=null && stage.sampleLessonIds().size()>=3,"Stage samples missing");
            for(String id:stage.sampleLessonIds())require(ids.contains(id) && stageTopics.contains(get(id).topicId()),"Invalid stage sample");
        }
        for(var lesson:lessons) {
            require(topics.contains(lesson.topicId()) && lesson.prerequisites().stream().allMatch(ids::contains),"Missing prerequisite/topic");
            visit(lesson.id(),new HashSet<>(),new HashSet<>());
        }
    }
    private void visit(String id,Set<String> stack,Set<String> done) {
        if(done.contains(id))return;
        require(stack.add(id),"Prerequisite cycle");
        for(String before:get(id).prerequisites())visit(before,stack,done);
        stack.remove(id);done.add(id);
    }
    private static void require(boolean value,String error) { if(!value)throw new IllegalStateException(error); }
    public List<Stage> stages() { return stages; }
    public List<Lesson> all() { return lessons; }
    public Lesson get(String id) { return lessons.stream().filter(l->l.id().equals(id)).findFirst()
        .orElseThrow(()->new ApiError(404,"关卡不存在或尚未开放")); }
}
