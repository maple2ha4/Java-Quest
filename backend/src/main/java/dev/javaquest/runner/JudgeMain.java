package dev.javaquest.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Separate executable jar: no Spring context, database connection, or public listener. */
public final class JudgeMain {
    public static void main(String[] args) throws Exception {
        String mode="docker"; boolean allow=false;
        for(String arg:args) {
            if(arg.startsWith("--mode=")) mode=arg.substring(7);
            else if(arg.equals("--allow-local-process")) allow=true;
            else throw new IllegalArgumentException("Unknown judge option: "+arg);
        }
        String token=System.getenv().getOrDefault("JUDGE_TOKEN","");
        if(token.length()<32) throw new IllegalArgumentException("JUDGE_TOKEN must have at least 32 characters");
        var runner=new JudgeRunner(mode,allow,System.getenv().getOrDefault("JUDGE_IMAGE","eclipse-temurin:21-jdk"));
        runner.checkReady(); // Fail closed: unavailable Docker never falls back to local execution.
        int port=Integer.parseInt(System.getenv().getOrDefault("JUDGE_PORT","8091"));
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),16);
        var json=new ObjectMapper();
        var slots=new Semaphore(2);
        server.createContext("/health",exchange->{
            if(!exchange.getRequestMethod().equals("GET")) { send(exchange,json,405,Map.of("message","Method not allowed"));return; }
            try { runner.checkReady();send(exchange,json,200,Map.of("status","ok","mode",runner.mode())); }
            catch(Exception e) { send(exchange,json,503,Map.of("message","Configured runner unavailable","mode",runner.mode())); }
        });
        server.createContext("/judge",exchange->{
            if(!exchange.getRequestMethod().equals("POST")) { send(exchange,json,405,Map.of("message","Method not allowed"));return; }
            String supplied=exchange.getRequestHeaders().getFirst("X-Judge-Token");
            if(supplied==null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) {
                send(exchange,json,401,Map.of("message","Unauthorized"));return;
            }
            String requested=exchange.getRequestHeaders().getFirst("X-Judge-Mode");
            if((runner.mode().equals("process") && !"process".equals(requested)) ||
                (runner.mode().equals("docker") && requested!=null && !Set.of("docker","remote").contains(requested))) {
                send(exchange,json,409,Map.of("message","Judge mode mismatch; nothing was executed"));return;
            }
            if(!slots.tryAcquire()) { send(exchange,json,429,Map.of("message","Judge is busy"));return; }
            try {
                byte[] body=exchange.getRequestBody().readNBytes(200001);
                if(body.length>200000) { send(exchange,json,413,Map.of("message","Request too large"));return; }
                var request=json.readValue(body,JudgeRunner.Request.class);
                JudgeRunner.validate(request);
                send(exchange,json,200,runner.judge(request));
            } catch(IllegalArgumentException|com.fasterxml.jackson.core.JsonProcessingException e) {
                send(exchange,json,400,Map.of("message","Invalid Java files or trusted tests"));
            } catch(Exception e) {
                System.err.println("Judge infrastructure failure: "+e.getClass().getSimpleName()+": "+e.getMessage());
                send(exchange,json,503,Map.of("message","Configured runner unavailable; no completion awarded"));
            } finally { slots.release();exchange.close(); }
        });
        server.setExecutor(Executors.newFixedThreadPool(4));
        Runtime.getRuntime().addShutdownHook(new Thread(()->{server.stop(1);runner.close();}));
        System.out.println("Java Quest judge listening on 127.0.0.1:"+port+"; explicit mode="+runner.mode());
        if(mode.equals("process")) System.out.println("TRUSTED LOCAL CODE ONLY: file/network/CPU/process permissions are NOT isolated.");
        server.start();
    }
    private static void send(HttpExchange exchange,ObjectMapper json,int status,Object body) throws IOException {
        byte[] bytes=json.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        exchange.sendResponseHeaders(status,bytes.length);
        try(var output=exchange.getResponseBody()) { output.write(bytes); }
    }
}
