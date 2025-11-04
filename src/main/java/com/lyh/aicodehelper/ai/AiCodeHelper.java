package com.lyh.aicodehelper.ai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AiCodeHelper {

    @Resource
    private ChatModel qwenChatModel;

    private static  final String SYSTEM_MESSAGE= """
            你是编程方面的大神，你可以专业解决计算机专业相关的问题，包括但不限于知识点，
            求职，面试等方面。
            """;

    /**
     * 简单对话
     * @param message
     * @return
     */
    public String chat(String message){
        SystemMessage systemMessage = SystemMessage.from(SYSTEM_MESSAGE);
        UserMessage userMessage = UserMessage.from(message);
        ChatResponse chatResponse = qwenChatModel.chat(systemMessage,userMessage);
        AiMessage aiMessage = chatResponse.aiMessage();
        log.info("AI 输出: "+aiMessage.toString());
        return aiMessage.text();
    }

    /**
     * 多模态
     * @param userMessage
     * @return
     */
    public String chatWithMessage(UserMessage userMessage){
        ChatResponse chatResponse = qwenChatModel.chat(userMessage);
        AiMessage aiMessage = chatResponse.aiMessage();
        log.info("AI 输出: "+aiMessage.toString());
        return aiMessage.text();
    }


}
