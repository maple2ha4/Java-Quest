package dev.javaquest;

import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class Accounts {
    private final JdbcTemplate db;
    private final boolean secure;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final SecureRandom random = new SecureRandom();
    private final Map<String,ArrayDeque<Long>> attempts = new ConcurrentHashMap<>();
    public Accounts(JdbcTemplate db, @Value("${quest.cookie-secure}") boolean secure) { this.db=db; this.secure=secure; }
    // Local development rate limiter; production needs shared storage and trusted proxy configuration.
    public synchronized void limit(String key, int maximum, long windowMs) {
        long now=System.currentTimeMillis();
        if(attempts.size()>10000) attempts.entrySet().removeIf(e->e.getValue().isEmpty() || e.getValue().getLast()<now-60000);
        var queue=attempts.computeIfAbsent(key,k->new ArrayDeque<>());
        while(!queue.isEmpty() && queue.getFirst()<=now-windowMs) queue.removeFirst();
        if(queue.size()>=maximum) throw new ApiError(429,"操作太频繁，请稍后重试");
        queue.addLast(now);
    }
    public long register(String email, String name, String password, HttpServletResponse response) {
        if(password.getBytes(StandardCharsets.UTF_8).length>72) throw new ApiError(400,"密码最多 72 个 UTF-8 字节");
        String normalized=email.strip().toLowerCase(Locale.ROOT);
        try { db.update("INSERT INTO app_user(email,display_name,password_hash) VALUES(?,?,?)",normalized,name.strip(),encoder.encode(password)); }
        catch(org.springframework.dao.DuplicateKeyException e) { throw new ApiError(409,"该邮箱已注册"); }
        long id=db.queryForObject("SELECT id FROM app_user WHERE email=?",Long.class,normalized);
        session(id,response); return id;
    }
    public long login(String email,String password,HttpServletResponse response) {
        var rows=db.queryForList("SELECT id,password_hash FROM app_user WHERE email=?",email.strip().toLowerCase(Locale.ROOT));
        // Always perform a BCrypt comparison to avoid a cheap account-existence timing oracle.
        String hash=rows.isEmpty()?"$2a$12$Xwgp1Wj1bI0z0YG7JnpKSe7m9R3BBjSZV1ZElk.KoeMIUog9AcJg2":(String)rows.getFirst().get("password_hash");
        boolean valid=encoder.matches(password,hash);
        if(rows.isEmpty() || !valid) throw new ApiError(401,"邮箱或密码不正确");
        long id=((Number)rows.getFirst().get("id")).longValue(); session(id,response); return id;
    }
    public long require(HttpServletRequest req) {
        String token=token(req);
        if(token.isEmpty()) throw new ApiError(401,"请先登录以保存学习进度");
        var ids=db.queryForList("SELECT user_id FROM auth_session WHERE token_hash=? AND expires_at>?",Long.class,hash(token),Timestamp.from(Instant.now()));
        if(ids.isEmpty()) throw new ApiError(401,"登录已过期，请重新登录");
        return ids.getFirst();
    }
    public void logout(HttpServletRequest req,HttpServletResponse res) {
        db.update("DELETE FROM auth_session WHERE token_hash=?",hash(token(req)));
        res.addHeader("Set-Cookie",cookie("",0));
    }
    private void session(long user,HttpServletResponse res) {
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.update("DELETE FROM auth_session WHERE expires_at<?",Timestamp.from(Instant.now()));
        db.update("INSERT INTO auth_session(token_hash,user_id,expires_at) VALUES(?,?,?)",hash(token),user,Timestamp.from(Instant.now().plusSeconds(604800)));
        res.addHeader("Set-Cookie",cookie(token,604800));
    }
    private String cookie(String value,long age) { return ResponseCookie.from("quest_session",value).httpOnly(true).secure(secure).sameSite("Strict").path("/api").maxAge(age).build().toString(); }
    private String token(HttpServletRequest req) {
        if(req.getCookies()!=null) for(var c:req.getCookies()) if(c.getName().equals("quest_session")) return c.getValue();
        return "";
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
