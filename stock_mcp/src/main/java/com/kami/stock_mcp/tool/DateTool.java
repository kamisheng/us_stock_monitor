package com.kami.stock_mcp.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 *
 * @Author kamiSheng
 * @Date 2026/10/3 10:18
 * @Description us_stock_monitor_dev
 * @Package com.kami.stock_mcp.tool
 */
@Component
@Slf4j
public class DateTool {
    @Tool(description = "获取当前时间")
    public String getDate() {
        String res = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return String.format("当前时间是：%s", res);
    }
    @Tool(description = "根据城市所在的时区Id来获取时间")
    public String getDateById(@ToolParam(description = "城市名称") String city,
                              @ToolParam(description = "时区Id") String id) {
        ZoneId zoneId = ZoneId.of(id);
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        String res = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return String.format("当前时间是：%s", res);
    }

}
