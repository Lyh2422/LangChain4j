package com.lyh.aicodehelper;

import com.lyh.aicodehelper.ai.AiCodeHelper;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiCodeHelperApplicationTests {

    @Resource
    private AiCodeHelper aiCodeHelper;

    @Test
    void chat() {
        aiCodeHelper.chat("你好，我叫熏鱼,我和女朋友闹矛盾了，因为她觉得我陪她的时间很短，应该怎么哄她？");
    }

    @Test
    void chatWithMessage() {
        UserMessage userMessage = UserMessage.from(
                TextContent.from("描述一下图片中是什么动物以及特征"),
                ImageContent.from("https://pic4.zhimg.com/100/v2-be3b1e3e842857c83ef7bf7734a0d2bf_r.jpg")
        );
        aiCodeHelper.chatWithMessage(userMessage);
    }
}
