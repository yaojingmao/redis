package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService voucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> seckillScript;

    static {
        seckillScript = new DefaultRedisScript<>();
        seckillScript.setResultType(Long.class);
        seckillScript.setLocation(new ClassPathResource("seckill.lua"));
    }

    @Resource
    private RedissonClient redissonClient;

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();


    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有消息，继续下一次循环
                        continue;
                    }
                    // 解析数据
//                    MapRecord<String, Object, Object> record = list.get(0);
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    createVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create("stream.orders", ReadOffset.from("0"))
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有异常消息，结束循环
                        break;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    createVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }
//    private class VoucherOrderHandler implements Runnable {
//
//
//        @Override
//        public void run() {
//
//            while (true) {
//                try {
////                从阻塞队列里面取任务
//                    VoucherOrder voucherOrder = orderTasks.take();
////                处理订单
//                    handlerVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("订单处理异常", e);
//                }
//            }
//        }
//
//        private void handlerVoucherOrder(VoucherOrder voucherOrder) {
//            // 5.一人一单
//            Long userId = voucherOrder.getUserId();
//
//            // 创建锁对象
//            RLock redisLock = redissonClient.getLock("lock:order:" + userId);
//            // 尝试获取锁
//            boolean isLock = redisLock.tryLock();
//            // 判断
//            if (!isLock) {
//                // 获取锁失败，直接返回失败或者重试
//                log.error("不允许重新下单");
//                return;
//            }
//            try {
//                proxy.createVoucherOrder(voucherOrder);
//            } finally {
//                redisLock.unlock();
//            }
//
//
//        }
//    }

    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
//        1.执行lua脚本
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        Long result = stringRedisTemplate.execute(
                seckillScript,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        System.out.println(result);
//        2.判断返回值是否是0
        int r = result.intValue();
        if (r != 0) {
//        2.1不为0，返回错误信息
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
//        2.2为0则有购买资格，把下单信息保存到阻塞队列中
//         保存到阻塞队列中
//        6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
//        设置订单id
        voucherOrder.setId(orderId);
//        设置用户id
        voucherOrder.setUserId(userId);
//        设置优惠卷id
        voucherOrder.setVoucherId(voucherId);
//        保存到阻塞队列中
        orderTasks.add(voucherOrder);

        proxy = (IVoucherOrderService) AopContext.currentProxy();

//        3.返回订单号
        return Result.ok(orderId);
    /*//       1.查询优惠卷信息
        SeckillVoucher seckillVoucher = voucherService.getById(voucherId);
//        2.判断优惠卷是否开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//        2.1没开始返回失败信息
            return Result.fail("活动还未开始");
        }

//        3.判断优惠卷是否结束
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//        3.1结束返回失败信息
            return Result.fail("活动已结束");
        }

//        4.判断库存是否充足
        if (seckillVoucher.getStock() < 1) {
//        4.1不充足返回失败信息
            return Result.fail("优惠卷已卖光");
        }
//        4.2实现一人一单问题，先查询数据库看是否已经购买过一次
        Long userId = UserHolder.getUser().getId();
//        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock simpleRedisLock = redissonClient.getLock("order:" + userId);
//       尝试获取锁
//        boolean success = simpleRedisLock.tryLock(1200);
        boolean success = simpleRedisLock.tryLock();
//        获取锁不成功则是他已经下了一单了
        if (!success) {
//            返回错误信息
            return Result.fail("一人只能下一单");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
//            释放锁
            simpleRedisLock.unlock();
        }*/
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
//        Long userId = UserHolder.getUser().getId();
        Long userId = voucherOrder.getUserId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
//        4.3已购买，则返回已购买信息
            log.error("已购买");
            return;
        }
//        5.充足扣减库存
        boolean success = voucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
//                使用乐观锁解决库存超卖问题
                .gt("stock", 0).update();
        if (!success) {
            log.error("优惠卷已卖光");
            return;
        }

        save(voucherOrder);

    }
}
