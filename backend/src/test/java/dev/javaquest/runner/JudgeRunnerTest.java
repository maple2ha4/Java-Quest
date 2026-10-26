package dev.javaquest.runner;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JudgeRunnerTest {
    private final JudgeRunner runner=new JudgeRunner("process",true,"unused");
    private JudgeRunner.Result run(String body,String expected) throws Exception {
        return runner.judge(new JudgeRunner.Request(List.of(new JudgeRunner.Source("Main.java",
            "public class Main { public static void main(String[] args) throws Exception { "+body+" } }")),
            List.of(new JudgeRunner.TestCase("",expected))));
    }
    @Test void requiresExplicitTrustOptInAndRejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class,()->new JudgeRunner("process",false,"unused"));
        assertThrows(IllegalArgumentException.class,()->JudgeRunner.validate(new JudgeRunner.Request(
            List.of(new JudgeRunner.Source("../Main.java","")),List.of(new JudgeRunner.TestCase("","")))));
    }
    @Test void realCompilationExecutionAndMultiFileInput() throws Exception {
        var result=runner.judge(new JudgeRunner.Request(List.of(
            new JudgeRunner.Source("Main.java","public class Main { public static void main(String[] args) { System.out.println(Helper.value()+new java.util.Scanner(System.in).nextInt()); } }"),
            new JudgeRunner.Source("Helper.java","public class Helper { static int value() { return 2; } }")),
            List.of(new JudgeRunner.TestCase("3\n","5\n"),new JudgeRunner.TestCase("8\n","10\n"))));
        assertTrue(result.passed());assertEquals(2,result.tests().size());assertEquals("process",result.mode());
        assertEquals("WRONG_ANSWER",run("System.out.println(1);","2").status());
    }
    @Test void packagedProjectCompilesAndRejectsUnsafePaths() throws Exception {
        for(String path:List.of("../Main.java","/Main.java","src/../../Main.java","C:/Main.java","src\\Main.java","src/./Main.java","NUL.java","src/CON/App.java"))
            assertFalse(JudgeRunner.validSourcePath(path),path);
        var result=runner.judge(new JudgeRunner.Request(List.of(
            new JudgeRunner.Source("src/main/java/demo/App.java","package demo; public class App { public static void main(String[] args) { System.out.println(new Helper().value()); } }"),
            new JudgeRunner.Source("src/main/java/demo/Helper.java","package demo; public class Helper { int value() { return 42; } }")),
            List.of(new JudgeRunner.TestCase("","42\n")),"demo.App"));
        assertTrue(result.passed(),result.toString());
        assertThrows(IllegalArgumentException.class,()->JudgeRunner.validate(new JudgeRunner.Request(
            List.of(new JudgeRunner.Source("Main.java","")),List.of(new JudgeRunner.TestCase("","")),"demo.Missing")));
    }
    @Test void compilerAndRuntimeFailuresAreReal() throws Exception {
        assertEquals("COMPILE_ERROR",run("BAD","anything").status());
        assertEquals("RUNTIME_ERROR",run("throw new IllegalArgumentException(\"test\");","").status());
    }
    @Test void stopsInfiniteLoopAndOutputFlood() throws Exception {
        assertEquals("TIME_LIMIT",run("while(true) {}","").status());
        var result=run("while(true) System.out.println(\"x\".repeat(1000));","");
        assertEquals("OUTPUT_LIMIT",result.status());
        assertTrue(result.tests().getFirst().stdout().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=65536);
    }
    @Test void learnerEnvironmentDoesNotInheritJudgeOrProxySecrets() throws Exception {
        var result=run("for(String n:new String[]{\"JUDGE_TOKEN\",\"DB_PASSWORD\",\"HTTP_PROXY\",\"HTTPS_PROXY\",\"JAVA_TOOL_OPTIONS\"}) { if(System.getenv(n)!=null) throw new AssertionError(n); } System.out.println(\"clean\");","clean");
        assertTrue(result.passed(),result.toString());
    }
}
