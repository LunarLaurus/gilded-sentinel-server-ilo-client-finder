package net.laurus.service;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.laurus.config.RedisConfig;
import net.laurus.interfaces.RedisCommand;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.args.BitOP;

/**
 * RedisClient is a service component that provides various Redis operations using Jedis.
 * It manages the Jedis connection pool and offers methods to interact with Redis data structures
 * such as Bitsets, Lists, and Hashes.
 */
@Slf4j
@Component
public class RedisClient {

    private final RedisConfig redisConfig;
    private JedisPool jedisPool;

    /**
     * Constructs a RedisClient with the specified Redis properties.
     *
     * @param redisConfig the Redis properties
     */
    public RedisClient(RedisConfig redisConfig) {
        this.redisConfig = redisConfig;
    }

    /**
     * Initializes the Jedis connection pool with configurations from RedisProperties.
     * This method sets the maximum total, maximum idle, and minimum idle connections.
     * It's called during the application context initialization.
     */
    @PostConstruct
    public void init() {
        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(redisConfig.getMaxTotal());
            poolConfig.setMaxIdle(redisConfig.getMaxIdle());
            poolConfig.setMinIdle(redisConfig.getMinIdle());

            jedisPool = new JedisPool(poolConfig, redisConfig.getHost(), redisConfig.getPort());
            log.info("Redis connection pool initialized successfully with host {} and port {}.", redisConfig.getHost(), redisConfig.getPort());
        } catch (Exception e) {
            log.error("Failed to initialize Jedis connection pool.", e);
        }
    }

    /**
     * Automatically closes Jedis pool at application shutdown. This method is
     * invoked before the application shuts down to release Redis connections.
     */
    @PreDestroy
    public void close() {
        if (jedisPool != null) {
            jedisPool.close();
            log.info("Redis pool closed.");
        }
    }

    /**
     * Executes a Redis command using a Jedis instance. This method abstracts the
     * common logic of getting a Jedis instance, executing a Redis command, and
     * logging the result. It ensures better code reusability and reduces
     * repetition.
     *
     * @param <T>            the return type of the Redis command
     * @param redisCommand   the command to execute
     * @param errorMessage   the error message to log in case of failure
     * @param successMessage the success message to log if the command is successful
     * @param successArgs    the arguments for the success message placeholders
     * @return the result of the Redis command, or null if an error occurred
     */
    private <T> T executeRedisCommand(RedisCommand<T> redisCommand, String errorMessage, String successMessage, Object... successArgs) {
        try (Jedis jedis = getJedis()) {
            if (jedis != null) {
                T result = redisCommand.execute(jedis);
                if (successMessage != null) {
                    log.info(successMessage, successArgs);
                }
                return result;
            }
            log.warn("Redis connection not available, cannot execute command.");
            return null;
        } catch (Exception e) {
            log.error(errorMessage, e);
            return null;
        }
    }

    /**
     * Retrieves a Jedis instance from the pool. If the pool is not initialized, a
     * warning is logged, and null is returned.
     *
     * @return Jedis instance if pool is available, null otherwise
     */
    private Jedis getJedis() {
        if (jedisPool != null) {
            return jedisPool.getResource();
        }
        log.warn("Redis pool is not initialized.");
        return null;
    }

    // ==================== Bitset Operations ====================

    /**
     * Counts the number of set bits (1s) in a Redis Bitset using the BITCOUNT command.
     *
     * @param bitsetKey the key of the bitset
     * @return the number of set bits in the bitset
     */
    public long countSetBits(String bitsetKey) {
        Long count = executeRedisCommand(
                jedis -> jedis.bitcount(bitsetKey),
                "Error counting bits in bitset '{}'.",
                "Retrieved bit count for bitset '{}': {}",
                bitsetKey, jedisPool != null ? jedisPool.toString() : "unknown", bitsetKey
        );
        return count != null ? count : 0L;
    }

    /**
     * Performs a bitwise operation between multiple Redis Bitsets and stores the
     * result in a new key using the BITOP command.
     *
     * @param operation  the bitwise operation to perform (AND, OR, XOR, NOT)
     * @param resultKey  the key to store the result of the operation
     * @param bitsetKeys the keys of the bitsets on which to perform the operation
     * @return the number of bits set in the result after the operation
     */
    public long performBitOperation(BitOP operation, String resultKey, String... bitsetKeys) {
        Long result = executeRedisCommand(
                jedis -> jedis.bitop(operation, resultKey, bitsetKeys),
                "Error performing BITOP operation '{}' on bitsets '{}'.",
                "Performed BITOP '{}' on bitsets '{}' and stored result in '{}'.",
                operation.name(), String.join(", ", bitsetKeys), resultKey
        );
        return result != null ? result : 0L;
    }

    /**
     * Finds the position of the first set bit (1) in a Redis Bitset using the BITPOS command.
     *
     * @param bitsetKey the key of the bitset
     * @return the position of the first set bit (1), or -1 if no set bit is found
     */
    public long findFirstSetBitPosition(String bitsetKey) {
        Long pos = executeRedisCommand(
                jedis -> jedis.bitpos(bitsetKey, true),
                "Error finding the position of the first set bit in bitset '{}'.",
                "Found first set bit at position '{}' in bitset '{}'.",
                bitsetKey
        );
        return pos != null ? pos : -1L;
    }

    /**
     * Finds the position of the first unset bit (0) in a Redis Bitset using the BITPOS command.
     *
     * @param bitsetKey the key of the bitset
     * @return the position of the first unset bit (0), or -1 if no unset bit is found
     */
    public long findFirstUnsetBitPosition(String bitsetKey) {
        Long pos = executeRedisCommand(
                jedis -> jedis.bitpos(bitsetKey, false),
                "Error finding the position of the first unset bit in bitset '{}'.",
                "Found first unset bit at position '{}' in bitset '{}'.",
                bitsetKey
        );
        return pos != null ? pos : -1L;
    }

    /**
     * Clears all bits in a Redis Bitset (sets all bits to 0) by deleting the key.
     *
     * @param bitsetKey the key of the bitset to clear
     */
    public void clearBitset(String bitsetKey) {
        executeRedisCommand(
                jedis -> {
                    jedis.del(bitsetKey);
                    return null;
                },
                "Error clearing bitset '{}'.",
                "Cleared bitset '{}'.",
                bitsetKey
        );
    }

    /**
     * Flips a specific bit (0 to 1, or 1 to 0) in a Redis Bitset using the SETBIT command.
     *
     * @param bitsetKey the key of the bitset
     * @param offset    the offset of the bit to flip
     * @return the original value of the bit before the flip
     */
    public boolean flipBit(String bitsetKey, long offset) {
        Boolean original = executeRedisCommand(
                jedis -> {
                    boolean currentBit = jedis.getbit(bitsetKey, offset);
                    jedis.setbit(bitsetKey, offset, !currentBit);
                    return currentBit;
                },
                "Error flipping bit at offset '{}' in bitset '{}'.",
                "Flipped bit at offset '{}' in bitset '{}'.",
                offset, bitsetKey
        );
        return original != null && original;
    }

    /**
     * Sets a bit in a Redis Bitset using the SETBIT command.
     *
     * @param bitsetKey the key of the bitset
     * @param offset    the offset of the bit to set
     * @param value     the value of the bit (true or false)
     */
    public void setBit(String bitsetKey, long offset, boolean value) {
        executeRedisCommand(
                jedis -> {
                    jedis.setbit(bitsetKey, offset, value);
                    return null;
                },
                "Error setting bit at offset '{}' in bitset '{}'.",
                "Set bit at offset '{}' in bitset '{}'.",
                offset, bitsetKey
        );
    }

    /**
     * Sets a range of bits in a Redis Bitset to a specific value (either true or false) using the SETBIT command.
     *
     * @param bitsetKey the key of the bitset
     * @param start     the starting offset of the range
     * @param end       the ending offset of the range
     * @param value     the value to set (true or false)
     */
    public void setBitRange(String bitsetKey, long start, long end, boolean value) {
        executeRedisCommand(
                jedis -> {
                    for (long i = start; i <= end; i++) {
                        jedis.setbit(bitsetKey, i, value);
                    }
                    return null;
                },
                "Error setting range of bits in bitset '{}' from offset '{}' to '{}'.",
                "Set bits in range '{}' to '{}' in bitset '{}'.",
                start, end, value, bitsetKey
        );
    }

    /**
     * Sets multiple bits in a Redis Bitset at once using the SETBIT command.
     *
     * @param bitsetKey  the key of the bitset
     * @param bitOffsets a map where the key is the bit offset and the value is the
     *                   bit value (true or false)
     */
    public void setMultipleBits(String bitsetKey, Map<Long, Boolean> bitOffsets) {
        executeRedisCommand(
                jedis -> {
                    bitOffsets.forEach((offset, value) -> jedis.setbit(bitsetKey, offset, value));
                    return null;
                },
                "Error setting multiple bits in bitset '{}'.",
                "Set multiple bits in bitset '{}'.",
                bitsetKey
        );
    }

    /**
     * Converts a Java BitSet to a Redis Bitset by storing each bit in Redis using SETBIT.
     *
     * @param bitsetKey the Redis key where the bitset will be stored
     * @param bitSet    the Java BitSet to be converted
     */
    public void convertJavaBitSetToRedis(String bitsetKey, BitSet bitSet) {
        executeRedisCommand(
                jedis -> {
                    for (int i = 0; i < bitSet.length(); i++) {
                        jedis.setbit(bitsetKey, i, bitSet.get(i));
                    }
                    return null;
                },
                "Error converting Java BitSet to Redis BitSet for key '{}'.",
                "Converted Java BitSet to Redis BitSet for key '{}'.",
                bitsetKey
        );
    }

    /**
     * Converts a Redis Bitset to a Java BitSet by retrieving each bit using GETBIT.
     *
     * @param bitsetKey the Redis key containing the bitset
     * @param length    the length of the bitset (maximum bit index to retrieve)
     * @return a Java BitSet representing the Redis Bitset
     */
    public BitSet convertRedisBitSetToJava(String bitsetKey, int length) {
        return executeRedisCommand(
                jedis -> {
                    BitSet bitSet = new BitSet();
                    for (int i = 0; i < length; i++) {
                        if (jedis.getbit(bitsetKey, i)) {
                            bitSet.set(i);
                        }
                    }
                    return bitSet;
                },
                "Error converting Redis BitSet to Java BitSet for key '{}'.",
                "Converted Redis BitSet to Java BitSet for key '{}'.",
                bitsetKey
        );
    }

    // ==================== String Operations ====================

    /**
     * Retrieves the value of a specific key from Redis using the GET command.
     *
     * @param key the Redis key
     * @return the value associated with the key, or null if not found or if an
     *         error occurs
     */
    public String getStringValue(String key) {
        return executeRedisCommand(
                jedis -> jedis.get(key),
                "Error retrieving value for key '{}'.",
                "Retrieved value '{}' for key '{}'.",
                key, key
        );
    }

    /**
     * Sets a key-value pair in Redis using the SET command.
     *
     * @param key   the Redis key
     * @param value the value to be set
     */
    public void setStringValue(String key, String value) {
        executeRedisCommand(
                jedis -> {
                    jedis.set(key, value);
                    return null;
                },
                "Error setting key-value pair in Redis.",
                "Set key '{}' with value '{}'.",
                key, value
        );
    }

    /**
     * Sets a key-value pair in Redis only if the key does not already exist using the SETNX command.
     *
     * @param key   the Redis key
     * @param value the value to be set
     * @return true if the key was set, false otherwise
     */
    public boolean setIfAbsent(String key, String value) {
        Long result = executeRedisCommand(
                jedis -> jedis.setnx(key, value),
                "Error setting key '{}' if absent.",
                "Set key '{}' if absent: {}.",
                key, value
        );
        return result != null && result == 1;
    }

    /**
     * Sets a key with a value and an expiration time using the SETEX command.
     *
     * @param key     the Redis key
     * @param seconds the expiration time in seconds
     * @param value   the value to be set
     */
    public void setStringValueWithExpiration(String key, int seconds, String value) {
        executeRedisCommand(
                jedis -> {
                    jedis.setex(key, seconds, value);
                    return null;
                },
                "Error setting key '{}' with expiration.",
                "Set key '{}' with expiration of {} seconds.",
                key, seconds
        );
    }

    /**
     * Retrieves the Time-To-Live (TTL) of a key using the TTL command.
     *
     * @param key the Redis key
     * @return the TTL of the key in seconds, or -1 if the key does not have an expiration
     */
    public long getTTL(String key) {
        Long ttl = executeRedisCommand(
                jedis -> jedis.ttl(key),
                "Error retrieving TTL for key '{}'.",
                "Retrieved TTL for key '{}': {} seconds.",
                key, key
        );
        return ttl != null ? ttl : -1L;
    }

    /**
     * Sets an expiration time on an existing key using the EXPIRE command.
     *
     * @param key     the Redis key
     * @param seconds the expiration time in seconds
     * @return true if the expiration was set, false otherwise
     */
    public boolean setExpiration(String key, int seconds) {
        Long result = executeRedisCommand(
                jedis -> jedis.expire(key, seconds),
                "Error setting expiration for key '{}'.",
                "Set expiration for key '{}' to {} seconds.",
                key, seconds
        );
        return result != null && result == 1;
    }

    // ==================== Hash Operations ====================

    /**
     * Retrieves all fields and values from a Redis Hash (map) using the HGETALL command.
     *
     * @param hashKey the key of the hash
     * @return a map of all fields and values in the hash
     */
    public Map<String, String> getAllHashFields(String hashKey) {
        return executeRedisCommand(
                jedis -> jedis.hgetAll(hashKey),
                "Error retrieving all fields from hash '{}'.",
                "Retrieved all fields from hash '{}'.",
                hashKey
        );
    }

    /**
     * Retrieves a value from a Redis Hash (map) by field using the HGET command.
     *
     * @param hashKey the key of the hash
     * @param field   the field within the hash
     * @return the value of the field in the hash, or null if not found
     */
    public String getHashField(String hashKey, String field) {
        return executeRedisCommand(
                jedis -> jedis.hget(hashKey, field),
                "Error retrieving field '{}' from hash '{}'.",
                "Retrieved field '{}' from hash '{}'.",
                field, hashKey
        );
    }

    /**
     * Sets a field-value pair in a Redis Hash (map) using the HSET command.
     *
     * @param hashKey the key of the hash
     * @param field   the field within the hash
     * @param value   the value to set for the field
     */
    public void setHashField(String hashKey, String field, String value) {
        executeRedisCommand(
                jedis -> {
                    jedis.hset(hashKey, field, value);
                    return null;
                },
                "Error setting field '{}' in hash '{}'.",
                "Set field '{}' in hash '{}' with value '{}'.",
                field, hashKey, value
        );
    }

    /**
     * Sets multiple field-value pairs in a Redis Hash (map) using the HMSET command.
     *
     * @param hashKey the key of the hash
     * @param map     a map of fields and their corresponding values
     */
    public void setMultipleHashFields(String hashKey, Map<String, String> map) {
        executeRedisCommand(
                jedis -> {
                    jedis.hmset(hashKey, map);
                    return null;
                },
                "Error setting multiple fields in hash '{}'.",
                "Set multiple fields in hash '{}' with values '{}'.",
                hashKey, map
        );
    }

    // ==================== List Operations ====================

    /**
     * Retrieves all elements from a Redis list using the LRANGE command.
     *
     * @param listKey the key of the list
     * @return a list of elements in the Redis list
     */
    public List<String> getAllListElements(String listKey) {
        return executeRedisCommand(
                jedis -> jedis.lrange(listKey, 0, -1),
                "Error retrieving list '{}' from Redis.",
                "Retrieved all elements from list '{}'.",
                listKey
        );
    }

    /**
     * Retrieves the length of a Redis list using the LLEN command.
     *
     * @param listKey the key of the list
     * @return the length of the list
     */
    public long getListLength(String listKey) {
        Long length = executeRedisCommand(
                jedis -> jedis.llen(listKey),
                "Error retrieving length of list '{}'.",
                "Retrieved length of list '{}': {}",
                listKey, listKey, listKey
        );
        return length != null ? length : 0L;
    }

    /**
     * Pushes an element to the head of a Redis list using the LPUSH command.
     *
     * @param listKey the key of the list
     * @param value   the value to push to the list
     */
    public void pushToListHead(String listKey, String value) {
        executeRedisCommand(
                jedis -> {
                    jedis.lpush(listKey, value);
                    return null;
                },
                "Error pushing value '{}' to the head of list '{}'.",
                "Pushed value '{}' to the head of list '{}'.",
                value, listKey
        );
    }

    /**
     * Pushes an element to the tail of a Redis list using the RPUSH command.
     *
     * @param listKey the key of the list
     * @param value   the value to push to the list
     */
    public void pushToListTail(String listKey, String value) {
        executeRedisCommand(
                jedis -> {
                    jedis.rpush(listKey, value);
                    return null;
                },
                "Error pushing value '{}' to the tail of list '{}'.",
                "Pushed value '{}' to the tail of list '{}'.",
                value, listKey
        );
    }

    /**
     * Removes and returns the first element of a Redis list using the LPOP command.
     *
     * @param listKey the key of the list
     * @return the first element of the list, or null if the list is empty
     */
    public String popFromListHead(String listKey) {
        return executeRedisCommand(
                jedis -> jedis.lpop(listKey),
                "Error popping value from the head of list '{}'.",
                "Popped value '{}' from the head of list '{}'.",
                listKey
        );
    }

    /**
     * Removes and returns the last element of a Redis list using the RPOP command.
     *
     * @param listKey the key of the list
     * @return the last element of the list, or null if the list is empty
     */
    public String popFromListTail(String listKey) {
        return executeRedisCommand(
                jedis -> jedis.rpop(listKey),
                "Error popping value from the tail of list '{}'.",
                "Popped value '{}' from the tail of list '{}'.",
                listKey
        );
    }

    // ==================== Key Operations ====================

    /**
     * Checks if a key exists in Redis using the EXISTS command.
     *
     * @param key the Redis key to check
     * @return true if the key exists, false otherwise
     */
    public boolean keyExists(String key) {
        Boolean exists = executeRedisCommand(
                jedis -> jedis.exists(key),
                "Error checking if key '{}' exists.",
                "Key '{}' existence check: {}.",
                key, key
        );
        return exists != null && exists;
    }

    /**
     * Deletes a key from Redis using the DEL command.
     *
     * @param key the Redis key to delete
     */
    public void deleteKey(String key) {
        executeRedisCommand(
                jedis -> {
                    jedis.del(key);
                    return null;
                },
                "Error deleting key '{}'.",
                "Deleted key '{}'.",
                key
        );
    }

    /**
     * Retrieves all keys matching a given pattern from Redis using the KEYS command.
     *
     * @param pattern the pattern to match keys (e.g., "user:*")
     * @return an array of matching keys
     */
    public String[] getKeysByPattern(String pattern) {
        return executeRedisCommand(
                jedis -> jedis.keys(pattern).toArray(new String[0]),
                "Error retrieving keys by pattern '{}'.",
                "Retrieved keys matching pattern '{}'.",
                pattern
        );
    }

    // ==================== Increment/Decrement Operations ====================
    
 // ==================== Get/Set Operations ====================

    /**
     * Gets the integer value of a key from Redis using the GET command.
     * If the key does not exist or the value is not an integer, it returns 0.
     *
     * @param key the Redis key to retrieve the value for
     * @return the value of the key or 0 if the key does not exist or is not a valid integer
     */
    public long getKeyValue(String key) {
        String value = executeRedisCommand(
                jedis -> jedis.get(key),
                "Error getting value for key '{}'.",
                "Retrieved long value for key '{}'.",
                key, key
        );
        try {
            return value != null ? Long.parseLong(value) : 0L;
        } catch (NumberFormatException e) {
            // In case the value is not a valid number, return 0
            return 0L;
        }
    }

    /**
     * Sets the integer value of a key in Redis using the SET command.
     * If the key already exists, its value will be overwritten.
     *
     * @param key the Redis key to set the value for
     * @param value the value to set for the key
     * @return true if the operation was successful, false otherwise
     */
    public boolean setKeyValue(String key, long value) {
        String result = executeRedisCommand(
                jedis -> jedis.set(key, String.valueOf(value)),
                "Error setting value for key '{}'.",
                "Set key '{}' to value '{}'.",
                key, value
        );
        return "OK".equals(result); // Redis SET command returns "OK" on success
    }


    /**
     * Increments the integer value of a key by 1 in Redis using the INCR command.
     * If the key does not exist, it will be set to 1.
     *
     * @param key the Redis key to increment
     * @return the new value after increment
     */
    public long incrementKey(String key) {
        Long newValue = executeRedisCommand(
                jedis -> jedis.incr(key),
                "Error incrementing key '{}'.",
                "Incremented key '{}'.",
                key, key
        );
        return newValue != null ? newValue : 0L;
    }

    /**
     * Decrements the integer value of a key by 1 in Redis using the DECR command.
     * If the key does not exist, it will be set to -1.
     *
     * @param key the Redis key to decrement
     * @return the new value after decrement
     */
    public long decrementKey(String key) {
        Long newValue = executeRedisCommand(
                jedis -> jedis.decr(key),
                "Error decrementing key '{}'.",
                "Decremented key '{}'.",
                key, key
        );
        return newValue != null ? newValue : 0L;
    }
    
 // ==================== Get/Set Boolean Operations ====================

    /**
     * Gets the boolean value of a key from Redis.
     * If the key does not exist, it returns false by default.
     *
     * @param key the Redis key to retrieve the value for
     * @return true if the key exists and its value is 1, false if the key is not found or its value is 0
     */
    public boolean getBooleanValue(String key) {
        String value = executeRedisCommand(
                jedis -> jedis.get(key),
                "Error getting value for key '{}'.",
                "Retrieved boolean value for key '{}'.",
                key, key
        );
        return "1".equals(value); // Returns true if the value is "1", otherwise false
    }

    /**
     * Sets the boolean value of a key in Redis.
     * The value will be stored as "1" for true and "0" for false.
     *
     * @param key the Redis key to set the value for
     * @param value the boolean value to set for the key
     * @return true if the operation was successful, false otherwise
     */
    public boolean setBooleanValue(String key, boolean value) {
        String result = executeRedisCommand(
                jedis -> jedis.set(key, value ? "1" : "0"),
                "Error setting boolean value for key '{}'.",
                "Set key '{}' to boolean value '{}'.",
                key, value
        );
        return "OK".equals(result); // Redis SET command returns "OK" on success
    }

}
