package com.kami.stock_mcp.tool;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import jakarta.annotation.Resource;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
public class EmailTool {
    @Resource
    private JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Tool(description = "接收方邮件 to，邮件标题 subject，邮件内容 content，给接收方发送邮件")
    public void sendEmail(@ToolParam(description = "接收方邮件") String to,
                            @ToolParam(description = "邮件标题") String subject,
                            @ToolParam(description = "邮件内容") String content,
                          @ToolParam(description = "邮件内容是否为markdown格式，若为markdown则为1，若为html则为2") Integer isMarkdown) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage);
        try{
            mimeMessageHelper.setFrom(from);
            mimeMessageHelper.setTo(to);
            mimeMessageHelper.setSubject(subject);
            if(isMarkdown == 1) {
                mimeMessageHelper.setText(convertToHtml(content), true);
            }else if(isMarkdown == 2) {
                mimeMessageHelper.setText(content, true);
            }else {
                mimeMessageHelper.setText(content);
            }
            javaMailSender.send(mimeMessage);
        } catch (Exception e) {
            log.error("============ 调用MCP工具：sendEmail({}, {}, {}, {}) 失败 =============",from, to, subject, content, e);
        }
    }
    public static String convertToHtml(String markdownContent) {

        MutableDataSet dataset = new MutableDataSet();
        Parser parser = Parser.builder(dataset).build();
        HtmlRenderer htmlRenderer = HtmlRenderer.builder(dataset).build();

        return htmlRenderer.render(parser.parse(markdownContent));
    }

}
