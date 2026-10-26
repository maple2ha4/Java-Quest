package dev.javaquest.runner;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Copies trusted, already-built application dependencies; never downloads learner dependencies. */
final class FrameworkLibraries implements AutoCloseable {
    private Path cache;
    private List<Path> libraries;
    synchronized List<Path> files() throws IOException {
        if(libraries!=null)return libraries;
        cache=Files.createTempDirectory("quest-framework-");
        var found=new LinkedHashMap<String,Path>();
        for(String item:System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")).split(java.io.File.pathSeparator)) {
            Path path=Path.of(item);
            if(!Files.isRegularFile(path) || !item.endsWith(".jar"))continue;
            try(var jar=new JarFile(path.toFile())) {
                var entries=jar.entries();boolean boot=false;
                while(entries.hasMoreElements()) {
                    var entry=entries.nextElement();
                    if(entry.getName().startsWith("BOOT-INF/lib/") && entry.getName().endsWith(".jar")) {
                        String name=Path.of(entry.getName()).getFileName().toString();
                        if(!name.matches("[A-Za-z0-9._-]+\\.jar"))throw new IOException("Invalid trusted library name");
                        Path target=cache.resolve(name);
                        try(var input=jar.getInputStream(entry)){Files.copy(input,target,StandardCopyOption.REPLACE_EXISTING);}
                        found.put(name,target);boot=true;
                    }
                }
                if(!boot)found.putIfAbsent(path.getFileName().toString(),path);
            }
        }
        if(found.keySet().stream().noneMatch(n->n.startsWith("spring-context-")))throw new IOException("Spring libraries were not bundled; rebuild the judge jar");
        libraries=List.copyOf(found.values());return libraries;
    }
    @Override public synchronized void close() {
        if(cache==null)return;
        try(var paths=Files.walk(cache)) { for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path); }
        catch(IOException ignored) { }
    }
}
