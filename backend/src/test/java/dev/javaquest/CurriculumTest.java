package dev.javaquest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CurriculumTest {
    private Curriculum curriculum() throws Exception { var c=new Curriculum(new ObjectMapper());c.load();return c; }
    @Test void everyPublishedLessonHasAProgressionAndNoPlannedContentIsExecutable() throws Exception {
        var c=curriculum();Set<String> listed=new HashSet<>();int planned=0;
        assertEquals(Set.of("java","collections","maven","spring","concurrency","engineering"),new HashSet<>(c.stages().stream().map(Curriculum.Stage::id).toList()));
        for(var stage:c.stages()) for(var chapter:stage.chapters()) for(var topic:chapter.topics()) {
            assertFalse(topic.exercises().isEmpty());
            assertNotNull(topic.practiceRationale());
            for(var exercise:topic.exercises()) {
                if(exercise.lessonId()==null)planned++;
                else { assertTrue(listed.add(exercise.lessonId()));assertEquals(topic.id(),c.get(exercise.lessonId()).topicId()); }
            }
        }
        assertEquals(new HashSet<>(c.all().stream().map(Curriculum.Lesson::id).toList()),listed);
        assertTrue(planned>0);
        var counts=c.stages().stream().flatMap(s->s.chapters().stream()).flatMap(ch->ch.topics().stream()).map(t->t.exercises().size()).collect(java.util.stream.Collectors.toSet());
        assertTrue(counts.size()>=4,"Practice counts should vary with topic needs");
        for(var stage:c.stages())assertTrue(stage.sampleLessonIds().size()>=3);
        assertEquals(List.of("hello-world","variables","types","operators","conditions","loops","arrays","methods","strings","exceptions"),c.all().subList(0,10).stream().map(Curriculum.Lesson::id).toList());
    }
    @Test void projectContractIsReadOnlyAtTheEntryAndAllLessonsHaveAuthorSolutions() throws Exception {
        var c=curriculum();var project=c.get("order-release");
        assertEquals("quest.orders.Application",project.entryClass());assertEquals("javac",project.buildKind());
        assertEquals(4,project.starter().size());assertFalse(project.editableFiles().stream().anyMatch(n->n.endsWith("Application.java")));
        assertTrue(project.starter().stream().allMatch(f->f.name().startsWith("src/main/java/")));
        assertTrue(project.tests().stream().anyMatch(t->t.expected().equals("ERROR\n")));
        try(var input=new ClassPathResource("course-solutions.json").getInputStream()) {
            var solutions=new ObjectMapper().readTree(input);
            for(var l:c.all())assertTrue(solutions.has(l.id()),"Missing author solution for "+l.id());
        }
    }
}
