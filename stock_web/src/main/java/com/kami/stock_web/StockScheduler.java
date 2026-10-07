package com.kami.stock_web;

import com.kami.stock_web.service.RssService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@EnableScheduling
@Component
@Slf4j
public class StockScheduler {

    @Resource
    private RssService rssService;

    /*
    定时任务，抓取股票信息
     */
    // 每分钟一轮：RSS 限额为 30 次/300 秒，每分钟 1 次只占用 20% 额度；
    // live.html 限额更严（10 次/300 秒），由 StockTitanCrawler 的 5 分钟缓存与最小重试间隔兜住
    @Scheduled(cron = "0 * * * * ?")
    public void getStockInfo() {
        try {
            rssService.displayRssZh();
        } catch (Exception e) {
            // 单次抓取失败不影响后续调度，只记录简要日志
            log.warn("抓取 RSS 失败: {}", e.toString());
            log.debug("抓取 RSS 失败详情", e);
        }
    }
}
