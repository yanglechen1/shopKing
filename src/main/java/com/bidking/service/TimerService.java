package com.bidking.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 每个房间独立管理倒计时，使用 ScheduledExecutorService
 * 严禁使用 @Scheduled（全局定时器无法按房间隔离）
 */
@Slf4j
@Service
public class TimerService {

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(20);

    /** 每个房间当前的定时任务，key=roomId */
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    /**
     * 启动出价倒计时
     * @param roomId    房间ID
     * @param seconds   倒计时秒数
     * @param onExpire  倒计时结束回调（进入 Grace Period）
     */
    public void startBidTimer(String roomId, int seconds, Runnable onExpire) {
        cancelTimer(roomId);
        log.info("[Timer] 房间={} 出价倒计时启动，{}秒", roomId, (Object) seconds);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            log.info("[Timer] 房间={} 出价倒计时结束，进入 Grace Period", roomId);
            onExpire.run();
        }, seconds, TimeUnit.SECONDS);
        futures.put(roomId, future);
    }

    /**
     * 启动 Grace Period 倒计时
     * @param roomId        房间ID
     * @param gracePeriodMs 冗余时间（毫秒）
     * @param onExpire      Grace Period 结束回调（触发判定）
     */
    public void startGraceTimer(String roomId, int gracePeriodMs, Runnable onExpire) {
        cancelTimer(roomId);
        log.info("[Timer] 房间={} Grace Period 启动，{}ms", roomId, (Object) gracePeriodMs);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            log.info("[Timer] 房间={} Grace Period 结束，触发判定", roomId);
            onExpire.run();
        }, gracePeriodMs, TimeUnit.MILLISECONDS);
        futures.put(roomId, future);
    }

    /** 取消房间当前定时任务 */
    public void cancelTimer(String roomId) {
        ScheduledFuture<?> old = futures.remove(roomId);
        if (old != null && !old.isDone()) {
            old.cancel(false);
            log.debug("[Timer] 房间={} 定时任务已取消", roomId);
        }
    }
}
