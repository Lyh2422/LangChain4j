package com.lyh.aicodehelper.ai;

import dev.langchain4j.service.SystemMessage;
import java.util.List;


public interface AiCodeHelperService {

    @SystemMessage(fromResource = "system-prompt.txt")
    String chat(String userMessage);


    @SystemMessage(fromResource = "system-prompt.txt")
    Report chatForReport(String userMessage);

    // 学习报告
    record Report(String name, List<String> suggestionList){}



}
