package com.network.identity;

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Component
class RateLimits {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> INCREMENT=new DefaultRedisScript<>("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n",Long.class);
    RateLimits(StringRedisTemplate redis) { this.redis=redis; }
    void require(String bucket,String key,int max,int seconds) {
        Long n=redis.execute(INCREMENT,List.of("rate:"+bucket+":"+DomainService.hash(key)),Integer.toString(seconds));
        if(n==null) throw new ResponseStatusException(SERVICE_UNAVAILABLE);
        if(n>max) throw new ResponseStatusException(TOO_MANY_REQUESTS,"Please wait before trying again");
    }
}
