
local id = redis.call('get', KEYS[1])
if (id == ARGV[1]) then
    return redis.call('del', KEYS[1])
end
return 0