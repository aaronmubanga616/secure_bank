package dev.securebank.auth;

import dev.securebank.common.ApiException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;

@Component
public class RateLimiter {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> COUNT = new DefaultRedisScript<>(
        "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n", Long.class);
    public RateLimiter(StringRedisTemplate redis) { this.redis = redis; }

    public long hit(String key, long maximum, Duration duration) {
        try {
            Long count = redis.execute(COUNT, List.of("rate:" + Crypto.hash(key)), String.valueOf(duration.toSeconds()));
            if (count == null) throw new IllegalStateException("Counter unavailable");
            if (count > maximum) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many attempts. Please try again later.");
            return count;
        } catch (ApiException error) { throw error; }
        catch (Exception error) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SECURITY_UNAVAILABLE", "Security checks are temporarily unavailable."); }
    }
}
