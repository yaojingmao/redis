---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by 86181.
--- DateTime: 2022/11/4 15:38
---

--- 优惠卷id
local voucherId = ARGV[1]
--- 用户id
local userId = ARGV[2]
--- 订单id
local orderId = ARGV[3]
--- 库存key
local stockKey = 'seckill:stock:' .. voucherId
--- 订单key
local orderKey = 'seckill:order:' .. voucherId

if (tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end
--- 判断订单是否存在
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end
--- 扣减库存
redis.call('incrby', stockKey, -1)

--- 添加订单
redis.call('sadd', orderKey, userId)
-- 发送消息到队列中
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)
return 0

