-- Sliding window log: a sorted set per rule, score = request time in ms, member = unique id.
-- KEYS[1] log key, ARGV[1] limit, ARGV[2] window ms, ARGV[3] unique suffix for the member.
-- Returns {allowed (1/0), count, oldestMs, nowMs}.
-- Unlike fixed window, the allow/deny decision is made here: a denied request must not be
-- written to the log (it would keep a hammering client blocked), and "count, then add only if
-- there is room" has to be one atomic step.
local limit = tonumber(ARGV[1])
local windowMs = tonumber(ARGV[2])

-- Redis's own clock, so every app instance shares one time source
local time = redis.call('TIME')
local nowMs = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)

-- drop entries that have slid out of the window (now - window, now]
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', string.format('%.0f', nowMs - windowMs))

local count = redis.call('ZCARD', KEYS[1])
local allowed = 0
if count < limit then
    redis.call('ZADD', KEYS[1], string.format('%.0f', nowMs), string.format('%.0f', nowMs) .. '-' .. ARGV[3])
    count = count + 1
    allowed = 1
end

-- idle keys clean themselves up
redis.call('PEXPIRE', KEYS[1], string.format('%.0f', windowMs))

-- the oldest entry tells the caller when the next slot frees up
local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
local oldestMs = nowMs
if oldest[2] then
    oldestMs = tonumber(oldest[2])
end

return { allowed, count, oldestMs, nowMs }
