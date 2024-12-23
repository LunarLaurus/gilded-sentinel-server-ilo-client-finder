package net.laurus.interfaces;

import redis.clients.jedis.Jedis;

/**
 * Functional interface representing a Redis command.
 *
 * @param <T> the return type of the Redis command
 */
@FunctionalInterface
public interface RedisCommand<T> {
    /**
     * Executes the Redis command using the provided Jedis instance.
     *
     * @param jedis the Jedis instance
     * @return the result of the command
     */
    T execute(Jedis jedis);
}
