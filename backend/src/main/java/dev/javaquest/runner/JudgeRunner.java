package dev.javaquest.runner;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Runs in a standalone coordinator JVM, never the Spring API JVM. */
public final class JudgeRunner implements AutoCloseable {
    public record Source(String name, String content) {}
    public record TestCase(String input, String expected) {}
    public record Request(List<Source> files, List<TestCase> tests) {}
    public record CaseResult(String status, boolean passed, String input, String expected,
                             String stdout, String stderr, long durationMs) {}
    public record Result(String status, boolean passed, List<CaseResult> tests, int totalTests, String mode) {}
    private record CommandResult(int exitCode, String stdout, String stderr, String limit) {}
    private static final int OUTPUT_LIMIT=65536;
    private static final Set<Process> ACTIVE=ConcurrentHashMap.newKeySet();
    private final Set<String> containers=ConcurrentHashMap.newKeySet();
    private final String mode;
    private final String image;

    public JudgeRunner(String mode, boolean allowLocalProcess, String image) {
        if(!Set.of("process","docker").contains(mode)) throw new IllegalArgumentException("Use process or docker explicitly");
        if(mode.equals("process") && !allowLocalProcess)
            throw new IllegalArgumentException("Process mode requires --allow-local-process; it is NOT a security sandbox");
        this.mode=mode;
        this.image=image;
    }
    public String mode() { return mode; }
    public void checkReady() throws Exception {
        if(mode.equals("process")) {
            if(Runtime.version().feature()<21 || !Files.isRegularFile(javaTool("javac")))
                throw new IllegalStateException("A complete JDK 21+ is required");
        } else {
            var check=execute(List.of("docker","image","inspect",image),null,"",Duration.ofSeconds(5));
            if(check.exitCode()!=0 || check.limit()!=null) throw new IOException("Docker or the configured JDK image is unavailable");
        }
    }
    public static void validate(Request request) {
        if(request==null || request.files()==null || request.tests()==null || request.files().isEmpty()
            || request.files().size()>8 || request.tests().isEmpty() || request.tests().size()>5)
            throw new IllegalArgumentException("Expected 1-8 Java files and 1-5 trusted tests");
        Set<String> names=new HashSet<>(); int size=0;
        for(var file:request.files()) {
            if(file==null || file.name()==null || file.content()==null
                || !file.name().matches("[A-Za-z][A-Za-z0-9_]{0,60}\\.java") || !names.add(file.name()))
                throw new IllegalArgumentException("Invalid or duplicate Java filename");
            size+=file.content().length();
        }
        if(!names.contains("Main.java") || size>32768) throw new IllegalArgumentException("Main.java required; source limit 32KiB characters");
        for(var test:request.tests()) if(test==null || test.input()==null || test.expected()==null
            || test.input().length()>16384 || test.expected().length()>16384)
            throw new IllegalArgumentException("Invalid trusted test");
    }
    public Result judge(Request request) throws Exception {
        validate(request);
        List<CaseResult> results=new ArrayList<>();
        for(var test:request.tests()) {
            var result=runCase(request.files(),test);
            results.add(result);
            if(Set.of("COMPILE_ERROR","TIME_LIMIT","OUTPUT_LIMIT").contains(result.status())) break;
        }
        boolean passed=results.size()==request.tests().size() && results.stream().allMatch(CaseResult::passed);
        String status=passed?"PASSED":results.stream().filter(t->!t.passed()).findFirst().orElseThrow().status();
        return new Result(status,passed,results,request.tests().size(),mode);
    }
    private CaseResult runCase(List<Source> files, TestCase test) throws Exception {
        long start=System.nanoTime();
        Path directory=Files.createTempDirectory("java-quest-run-");
        String container="jq-java-"+UUID.randomUUID();
        boolean docker=mode.equals("docker");
        try {
            for(var file:files) Files.writeString(directory.resolve(file.name()),file.content(),StandardCharsets.UTF_8);
            List<String> compile=new ArrayList<>();
            List<String> run=new ArrayList<>();
            if(docker) {
                // No host mounts, secrets, network or Docker socket in the learner container.
                containers.add(container);
                required(List.of("docker","run","--rm","--name",container,"--pull=never","-d",
                    "--network=none","--read-only","--user=65534:65534","--cap-drop=ALL",
                    "--security-opt=no-new-privileges:true","--pids-limit=64","--memory=256m",
                    "--memory-swap=256m","--cpus=1","--ulimit=nofile=128:128","--ulimit=fsize=16384:16384",
                    "--tmpfs=/work:rw,nosuid,nodev,size=64m,mode=1777","--workdir=/work",image,"sleep","60"),Duration.ofSeconds(10));
                // Stream a minimal tar into tmpfs. docker cp rejects read-only rootfs on some daemons.
                // No host mounts and no untrusted values are interpolated into shell commands.
                var copy=executeBytes(List.of("docker","exec","-i",container,"tar","xf","-"),null,archive(files),Duration.ofSeconds(5));
                if(copy.exitCode()!=0 || copy.limit()!=null) throw new IOException("Source transfer failed: "+copy.stderr());
                compile.addAll(List.of("docker","exec",container,"javac"));
                run.addAll(List.of("docker","exec","-i",container,"java"));
            } else {
                compile.add(javaTool("javac").toString());
                run.add(javaTool("java").toString());
            }
            compile.addAll(List.of("-J-Xmx96m","-J-XX:ActiveProcessorCount=1","-proc:none","-encoding","UTF-8"));
            for(var file:files) compile.add(file.name());
            var compilation=execute(compile,directory,"",Duration.ofSeconds(15));
            if(compilation.limit()!=null || compilation.exitCode()!=0) {
                String status=compilation.limit()!=null?compilation.limit():"COMPILE_ERROR";
                return result(status,compilation,test,start);
            }
            run.addAll(List.of("-Xmx96m","-XX:MaxMetaspaceSize=64m","-XX:ActiveProcessorCount=1","-Dfile.encoding=UTF-8","-cp",".","Main"));
            var execution=execute(run,directory,test.input(),Duration.ofSeconds(3));
            String status=execution.limit()!=null?execution.limit():execution.exitCode()!=0?"RUNTIME_ERROR":
                normalize(execution.stdout()).equals(normalize(test.expected()))?"PASSED":"WRONG_ANSWER";
            return result(status,execution,test,start);
        } finally {
            if(docker) {
                try { execute(List.of("docker","rm","-f",container),null,"",Duration.ofSeconds(8)); }
                catch(Exception e) { System.err.println("Could not remove owned container "+container); }
                containers.remove(container);
            }
            // Best effort: process mode cannot contain deliberately escaped/detached hostile programs.
            try(var paths=Files.walk(directory)) {
                for(Path path:paths.sorted(Comparator.reverseOrder()).toList()) {
                    try { Files.deleteIfExists(path); } catch(IOException ignored) { path.toFile().deleteOnExit(); }
                }
            }
        }
    }
    private static CaseResult result(String status,CommandResult command,TestCase test,long start) {
        return new CaseResult(status,status.equals("PASSED"),test.input(),test.expected(),command.stdout(),command.stderr(),
            Duration.ofNanos(System.nanoTime()-start).toMillis());
    }
    @Override public void close() {
        // Shutdown cleanup is best effort, not an isolation boundary for hostile local code.
        for(var process:List.copyOf(ACTIVE)) {
            process.descendants().forEach(child->{ if(child.isAlive()) child.destroyForcibly(); });
            if(process.isAlive()) process.destroyForcibly();
        }
        for(String name:List.copyOf(containers)) {
            try { execute(List.of("docker","rm","-f",name),null,"",Duration.ofSeconds(8)); }
            catch(Exception ignored) { } // --rm + 60s container lifetime is an additional cleanup bound.
        }
    }
    /** Minimal POSIX tar for already-validated flat source filenames; no archive input is accepted. */
    private static byte[] archive(List<Source> files) throws IOException {
        var output=new ByteArrayOutputStream();
        for(var file:files) {
            byte[] body=file.content().getBytes(StandardCharsets.UTF_8), header=new byte[512];
            byte[] name=file.name().getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(name,0,header,0,name.length);
            octal(header,100,8,0644);octal(header,108,8,65534);octal(header,116,8,65534);
            octal(header,124,12,body.length);octal(header,136,12,0);
            Arrays.fill(header,148,156,(byte)' ');header[156]='0';
            byte[] magic="ustar".getBytes(StandardCharsets.US_ASCII);System.arraycopy(magic,0,header,257,magic.length);
            header[263]='0';header[264]='0';
            long sum=0;for(byte value:header) sum+=value&255;
            octal(header,148,7,sum);header[155]=' ';
            output.write(header);output.write(body);output.write(new byte[(512-body.length%512)%512]);
        }
        output.write(new byte[1024]);return output.toByteArray();
    }
    private static void octal(byte[] header,int offset,int length,long value) {
        var text=String.format(java.util.Locale.ROOT,"%0"+(length-1)+"o",value).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(text,0,header,offset,text.length);
    }
    private static String normalize(String value) { return value.replace("\r\n","\n").stripTrailing(); }
    private static Path javaTool(String name) {
        return Path.of(System.getProperty("java.home"),"bin",name+(System.getProperty("os.name").startsWith("Windows")?".exe":""));
    }
    private static void required(List<String> command,Duration budget) throws Exception {
        var result=execute(command,null,"",budget);
        if(result.exitCode()!=0 || result.limit()!=null) throw new IOException("Docker prerequisite failed: "+result.stderr());
    }
    private static final class Capture {
        final ByteArrayOutputStream out=new ByteArrayOutputStream(), err=new ByteArrayOutputStream();
        boolean overflow;
        synchronized void add(byte[] bytes,int length,boolean stderr) {
            int remaining=OUTPUT_LIMIT-out.size()-err.size();
            (stderr?err:out).write(bytes,0,Math.min(remaining,length));
            if(length>remaining) overflow=true;
        }
        synchronized boolean overflow() { return overflow; }
        synchronized String stdout() { return out.toString(StandardCharsets.UTF_8); }
        synchronized String stderr() { return err.toString(StandardCharsets.UTF_8); }
    }
    private static Thread read(InputStream stream,Capture capture,boolean stderr) {
        return Thread.ofVirtual().start(()->{
            try(stream) { byte[] buffer=new byte[8192]; int length;
                while((length=stream.read(buffer))!=-1) capture.add(buffer,length,stderr);
            } catch(IOException ignored) { }
        });
    }
    private static CommandResult execute(List<String> command,Path directory,String input,Duration budget) throws Exception {
        return executeBytes(command,directory,input.getBytes(StandardCharsets.UTF_8),budget);
    }
    private static CommandResult executeBytes(List<String> command,Path directory,byte[] input,Duration budget) throws Exception {
        ProcessBuilder builder=new ProcessBuilder(command);
        if(directory!=null) builder.directory(directory.toFile());
        // Never inject API/database/judge secrets, proxy bindings, or Java injection options into learner JVMs.
        var inherited=new HashMap<>(builder.environment());
        builder.environment().clear();
        for(String key:List.of("SystemRoot","SYSTEMROOT","WINDIR","ComSpec","PATHEXT","PATH","TEMP","TMP","LANG","LC_ALL","TZ"))
            if(inherited.containsKey(key)) builder.environment().put(key,inherited.get(key));
        // Docker needs its existing client context/proxy configuration; these are not passed into containers.
        if(command.getFirst().equals("docker")) {
            for(var e:inherited.entrySet()) if(e.getKey().startsWith("DOCKER_") || Set.of("HOME","USERPROFILE","HTTP_PROXY","HTTPS_PROXY","NO_PROXY").contains(e.getKey()))
                builder.environment().put(e.getKey(),e.getValue());
        }
        Process process=builder.start();
        ACTIVE.add(process);
        Set<ProcessHandle> descendants=new HashSet<>();
        Capture capture=new Capture();
        Thread stdout=read(process.getInputStream(),capture,false), stderr=read(process.getErrorStream(),capture,true);
        Thread writer=Thread.ofVirtual().start(()->{
            try(var stream=process.getOutputStream()) { stream.write(input); }
            catch(IOException ignored) { }
        });
        String limit=null; long deadline=System.nanoTime()+budget.toNanos();
        try {
            while(true) {
                process.descendants().forEach(descendants::add);
                if(capture.overflow()) { limit="OUTPUT_LIMIT";break; }
                if(!process.isAlive()) break;
                if(System.nanoTime()>deadline) { limit="TIME_LIMIT";break; }
                Thread.sleep(20);
            }
        } finally {
            process.descendants().forEach(descendants::add);
            for(var child:descendants) if(child.isAlive()) child.destroyForcibly();
            if(process.isAlive()) process.destroyForcibly();
            process.waitFor(3,TimeUnit.SECONDS);
            stdout.join(1500);stderr.join(1500);writer.join(500);
            ACTIVE.remove(process);
        }
        if(capture.overflow()) limit="OUTPUT_LIMIT";
        return new CommandResult(process.isAlive()?-1:process.exitValue(),capture.stdout(),capture.stderr(),limit);
    }
}
