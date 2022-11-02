package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDto;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService voucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
//       1.查询优惠卷信息
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
        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//       尝试获取锁
        boolean success = simpleRedisLock.tryLock(1200);
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
            simpleRedisLock.unLock();
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
//        4.3已购买，则返回已购买信息
            return Result.fail("已购买");
        }
//        5.充足扣减库存
        boolean success = voucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherId)
//                使用乐观锁解决库存超卖问题
                .gt("stock", 0).update();
        if (!success) {
            return Result.fail("优惠卷已卖光");
        }
//        6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
//        设置订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
//        设置用户id
        UserDto user = UserHolder.getUser();
        voucherOrder.setUserId(userId);
//        设置优惠卷id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
//        7.返回订单id
        return Result.ok(orderId);
    }
}
