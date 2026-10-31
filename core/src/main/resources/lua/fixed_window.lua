-- Fixed window counter.
-- KEYS[1] counter key, ARGV[1] limit, ARGV[2] window seconds.
-- Returns {count, ttlSeconds}. The allow/deny comparison against the limit (ARGV[1])
-- is done in Java, so the script only counts.
local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
end
return { count, redis.call('TTL', KEYS[1]) }
