## 一、文档概述
本文档详细说明Java如何集成主流AI大模型（含云模型如OpenAI、百度文心，本地模型如Llama、Qwen），提供完整依赖配置、核心代码与参数说明，适配AI生成、对话等场景。


## 二、核心知识点
### 1. Java集成AI的核心依赖与工具
- HTTP客户端：`okhttp3`（调用云模型API）、`Retrofit`（封装API请求）
- AI框架：`LangChain4j`（简化大模型调用，支持多模型统一接口）、`LLamaCpp-Java`（本地模型调用）
- JSON解析：`jackson-databind`（解析模型JSON响应）

### 2. 集成云模型（以OpenAI、百度文心为例）
#### 2.1 集成OpenAI GPT-3.5/4
##### 2.1.1 依赖配置（Maven）
```xml
<dependencies>
    <!-- LangChain4j（简化OpenAI调用） -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-openai</artifactId>
        <version>0.24.0</version>
    </dependency>
    <!-- 日志依赖（可选） -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-simple</artifactId>
        <version>2.0.9</version>
    </dependency>
</dependencies>
```

##### 2.1.2 核心代码（对话功能）
```java
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;

public class OpenAIClient {
    // 替换为你的OpenAI API Key
    private static final String OPENAI_API_KEY = "sk-your-openai-api-key";

    public static void main(String[] args) {
        // 1. 创建OpenAI模型实例（指定模型版本、超时时间）
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(OPENAI_API_KEY)
                .modelName("gpt-3.5-turbo") // 模型名称：gpt-3.5-turbo/gpt-4
                .timeout(30000) // 超时时间30秒
                .temperature(0.7) // 随机性：0（精准）-1（ creative）
                .build();

        // 2. 构建用户请求（支持多轮对话，需传入历史ChatMessage）
        String userPrompt = "用Java写一个简单的RAG文档加载工具类";
        List<ChatMessage> messages = List.of(UserMessage.from(userPrompt));

        // 3. 调用模型并获取响应
        model.generate(messages).onSuccess(response -> {
            System.out.println("OpenAI响应：" + response.content());
        }).onFailure(e -> {
            System.err.println("调用失败：" + e.getMessage());
        });
    }
}
```

#### 2.2 集成百度文心一言（ERNIE）
##### 2.2.1 依赖配置（Maven）
```xml
<dependency>
    <groupId>com.baidu.aip</groupId>
    <artifactId>java-sdk</artifactId>
    <version>4.16.14</version>
</dependency>
```

##### 2.2.2 核心代码（文本生成）
```java
import com.baidu.aip.nlp.AipNlp;
import org.json.JSONObject;
import java.util.HashMap;

public class ErnieClient {
    // 替换为你的百度文心API密钥
    private static final String APP_ID = "your-app-id";
    private static final String API_KEY = "your-api-key";
    private static final String SECRET_KEY = "your-secret-key";

    public static void main(String[] args) {
        // 1. 初始化客户端
        AipNlp client = new AipNlp(APP_ID, API_KEY, SECRET_KEY);
        // 设置请求参数（超时时间、是否返回日志）
        client.setConnectionTimeoutInMillis(30000);
        client.setSocketTimeoutInMillis(30000);

        // 2. 构建请求参数
        String text = "解释什么是RAG技术";
        HashMap<String, Object> options = new HashMap<>();
        options.put("temperature", 0.7); // 随机性
        options.put("top_p", 0.9); // 采样阈值

        // 3. 调用文心一言生成接口
        JSONObject response = client.chatCompletions(text, options);
        System.out.println("文心一言响应：" + response.getJSONObject("result").getString("output"));
    }
}
```

### 3. 集成本地模型（以Llama 3、Qwen为例）
#### 3.1 依赖配置（LLamaCpp-Java）
```xml
<dependency>
    <groupId>ai.h2o</groupId>
    <artifactId>llama-cpp-java</artifactId>
    <version>0.1.7</version>
</dependency>
```

#### 3.2 集成Llama 3（本地部署）
```java
import ai.h2o.llama.LlamaModel;
import ai.h2o.llama.LlamaParams;

public class LocalLlamaClient {
    // 本地Llama 3模型文件路径（如：llama-3-8b-instruct.Q4_K_M.gguf）
    private static final String MODEL_PATH = "D:/models/llama-3-8b-instruct.Q4_K_M.gguf";

    public static void main(String[] args) {
        // 1. 加载本地模型（指定模型路径、参数）
        LlamaParams params = LlamaParams.builder()
                .nCtx(2048) // 上下文窗口大小（根据模型支持配置）
                .nThreads(4) // 线程数（建议等于CPU核心数）
                .build();

        try (LlamaModel model = LlamaModel.load(MODEL_PATH, params)) {
            // 2. 构建Prompt（遵循Llama 3指令格式）
            String prompt = "<|begin_of_text|><|start_header_id|>user<|end_header_id|>\n" +
                           "用Java实现一个文本分割工具类<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n";

            // 3. 生成响应
            String response = model.generate(prompt, 500); // 最大生成500个token
            System.out.println("Llama 3响应：" + response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```


## 三、实践注意事项
1. 云模型API密钥管理：避免硬编码，使用Spring Boot配置文件（`application.yml`）或环境变量存储，生产环境可加密（如Spring Cloud Config）
2. 本地模型配置：根据模型大小（如Llama 3 8B需约4GB内存）调整JVM内存（`-Xmx8g`），避免OOM
3. 超时与重试：云模型调用需添加重试机制（如`spring-retry`），处理网络波动导致的调用失败


## 四、常见问题（FAQ）
1. Q：LangChain4j支持哪些云模型？  
   A：支持OpenAI、Anthropic Claude、Google Gemini、百度文心、阿里通义千问等，提供统一`ChatModel`接口，切换模型只需修改依赖和配置。
2. Q：本地模型运行缓慢如何优化？  
   A：选择量化版本模型（如Q4_K_M，内存占用低），增加线程数（`nThreads`），调整上下文窗口（`nCtx`）至合理大小（避免过大占用内存）。


## 五、扩展资源
- LangChain4j文档：[LangChain4j官方指南](https://docs.langchain4j.dev/)
- 本地模型资源：[Hugging Face Models](https://huggingface.co/models)（下载Llama、Qwen等模型）
- 百度文心API文档：[百度智能云文心一言API](https://cloud.baidu.com/doc/WENXINWORKSHOP/index.html)