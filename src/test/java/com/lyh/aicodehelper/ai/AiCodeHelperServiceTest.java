package com.lyh.aicodehelper.ai;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiCodeHelperServiceTest {

    @Resource
    private AiCodeHelperService aiCodeHelperService;

    @Test
    void chat() {
        String result = aiCodeHelperService.chat("你好我，我叫熏鱼");
        System.out.println(result);
    }

    @Test
    void chatWithMemory() {
        String result = aiCodeHelperService.chat("你好我，我叫熏鱼");
        System.out.println(result);
        result=aiCodeHelperService.chat("我叫什么来着?");
        System.out.println(result);
    }

    @Test
    void chatForReport() {
        String userMessage = "你好，我叫熏鱼，学编程两年半，请帮我制定学习报告";
        AiCodeHelperService.Report report = aiCodeHelperService.chatForReport(userMessage);
        System.out.println(report);
    }

    @Test
    void chatWithRAG(){
        String result=aiCodeHelperService.chat("RAG的核心流程大概说一下");
        System.out.println(result);
    }

    @Test
    void chatWithTools() {
        String result = aiCodeHelperService.chat("有哪些常见的计算机网络面试题？");
        System.out.println(result);
    }
}