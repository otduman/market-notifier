package com.dumanch1.marketnotifier.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    // Why do we need a custom RedisTemplate bean?
    //
    // By default, Spring Data Redis uses JdkSerializationRedisSerializer which:
    //   - Stores keys as unreadable binary-serialized Java bytes
    //   - Has security risks (Java deserialization vulnerabilities)
    //   - Is not cross-language compatible
    //
    // NOTE on serializer history (relevant since we're on Spring Boot 4.0):
    //   - GenericJackson2JsonRedisSerializer  → @Deprecated since Spring Data Redis 4.0,
    //                                           marked forRemoval = true. Do NOT use.
    //   - GenericJacksonJsonRedisSerializer   → The NEW replacement, uses a builder API:
    //
    //       GenericJacksonJsonRedisSerializer.builder()
    //           .typePropertyName("_type")       // property name for type hints in JSON
    //           .enableUnsafeDefaultTyping()      // allows polymorphic deserialization
    //           .build();
    //
    //     Use this when you need to store complex polymorphic Java objects in Redis
    //     and deserialize them back to their original type automatically.
    //
    // Why we do NOT use either JSON serializer in this project:
    //
    // Every value we store in Redis is a plain String we construct ourselves:
    //   - Sorted Set members: "65432.10:1712345678900"  (price:timestamp)
    //   - List members:       "🔻 ALERT [BTCUSDT] DOWN moved..." (alert message)
    //
    // We parse these strings back manually in PriceStorageService and AlertService.
    // Jackson is not involved in reading them back — we do it ourselves with split(":").
    //
    // Therefore, the cleanest and fastest choice is StringRedisSerializer for BOTH
    // keys and values. No Jackson overhead, no @class type hint pollution in Redis,
    // and fully human-readable when you inspect Redis with redis-cli.
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use pure string serialization for everything — keys, values, hash keys, hash values.
        // Result in Redis CLI:
        //   KEYS *             → "prices:BTCUSDT", "alerts:history"
        //   ZRANGE prices:BTCUSDT 0 -1 → "65432.10:1712345678900"
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        // afterPropertiesSet() applies all the above configuration.
        // Must be called when configuring RedisTemplate manually.
        template.afterPropertiesSet();

        return template;
    }
}