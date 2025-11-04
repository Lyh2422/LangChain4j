## 一、文档概述
本文档以“Java智能知识库问答系统”为例，完整展示Java AI项目的开发流程，涵盖项目架构、核心模块实现、部署测试，集成RAG与大模型，适配本地知识库问答场景（如企业文档问答、技术手册查询）。


## 二、项目整体架构
### 1. 架构图
```
用户层（Web前端/API调用）
    ↓
接口层（Spring Boot Controller）
    ↓
业务层（RAG服务 + AI模型服务）
    ↓
数据层（本地知识库 + 向量数据库 + 缓存）
```

### 2. 技术栈
- 后端框架：Spring Boot 3.2.x
- AI集成：LangChain4j 0.24.0（大模型调用）、Sentence-BERT（向量生成）
- 向量存储：FAISS（本地向量库，适合小规模知识库）
- 文档处理：Apache PDFBox（PDF加载）、commons-io（TXT加载）
- 前端：Vue 3（可选，用于用户交互界面）
- 部署：Docker（容器化部署）


## 三、项目开发步骤
### 1. 步骤1：项目初始化（Spring Boot）
#### 1.1 创建Maven项目，配置`pom.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>
    <groupId>com.javaai</groupId>
    <artifactId>knowledge-qa-system</artifactId>
    <version>1.0-SNAPSHOT</version>

    <dependencies>
        <!-- Spring Boot核心 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- AI相关 -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-openai</artifactId>
            <version>0.24.0</version>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-embeddings-sentence-transformers</artifactId>
            <version>0.24.0</version>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-vectorstores-faiss</artifactId>
            <version>0.24.0</version>
        </dependency>

        <!-- 文档处理 -->
        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>pdfbox</artifactId>
            <version>2.0.32</version>
        </dependency>
        <dependency>
            <groupId>commons-io</groupId>
            <artifactId>commons-io</artifactId>
            <version>2.15.0</version>
        </dependency>

        <!-- 日志 -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>2.0.9</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

#### 1.2 配置`application.yml`（模型密钥、知识库路径）
```yaml
spring:
  application:
    name: knowledge-qa-system

# AI模型配置（OpenAI）
ai:
  openai:
    api-key: sk-your-openai-api-key
    model-name: gpt-3.5-turbo
    timeout: 30000

# 知识库配置
knowledge:
  path: D:/knowledge-base # 本地知识库文件夹（存放PDF/TXT）
  chunk-size: 500 # 文本分割长度
  chunk-overlap: 50 # 文本重叠长度
  top-k: 3 # RAG检索Top K相似片段

# 服务器配置
server:
  port: 8080
```

### 2. 步骤2：核心模块实现
#### 2.1 文档加载与处理模块（复用文档3的`DocumentLoader`和`TextSplitter`）
#### 2.2 向量存储模块（基于FAISS）
```java
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.vectorstore.FaissVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class FaissVectorStoreService {
    private FaissVectorStore vectorStore;

    @Value("${knowledge.path}")
    private String knowledgePath;
    @Value("${knowledge.chunk-size}")
    private int chunkSize;
    @Value("${knowledge.chunk-overlap}")
    private int chunkOverlap;

    private final EmbeddingModel embeddingModel;
    private final DocumentLoader documentLoader;
    private final TextSplitter textSplitter;

    // 构造函数注入依赖
    public FaissVectorStoreService(EmbeddingModel embeddingModel, 
                                   DocumentLoader documentLoader, 
                                   TextSplitter textSplitter) {
        this.embeddingModel = embeddingModel;
        this.documentLoader = documentLoader;
        this.textSplitter = new TextSplitter(chunkSize, chunkOverlap);
    }

    /**
     * 项目启动时初始化向量库（加载知识库文档→分割→生成向量→存储）
     */
    @PostConstruct
    public void initVectorStore() throws IOException {
        // 1. 读取知识库文件夹下的所有PDF/TXT文件
        Path path = Paths.get(knowledgePath);
        try (Stream<Path> fileStream = Files.list(path)) {
            List<Path> files = fileStream.filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".pdf") || f.toString().endsWith(".txt"))
                    .collect(Collectors.toList());

            // 2. 加载所有文档并分割
            List<TextSegment> textSegments = files.stream()
                    .flatMap(file -> {
                        try {
                            String text = file.toString().endsWith(".pdf") 
                                    ? documentLoader.loadPdf(file.toString()) 
                                    : documentLoader.loadTxt(file.toString());
                            List<String> chunks = textSplitter.split(text);
                            // 转换为TextSegment（LangChain4j格式，含元数据）
                            return chunks.stream().map(chunk -> TextSegment.from(chunk, 
                                    "filePath", file.toString()));
                        } catch (IOException e) {
                            throw new RuntimeException("加载文件失败：" + file, e);
                        }
                    })
                    .collect(Collectors.toList());

            // 3. 生成向量并初始化FAISS向量库
            List<Embedding> embeddings = embeddingModel.embedAll(textSegments).content();
            this.vectorStore = FaissVectorStore.builder()
                    .embeddings(embeddings)
                    .textSegments(textSegments)
                    .build();
            System.out.println("FAISS向量库初始化完成，存储文本片段数：" + textSegments.size());
        }
    }

    /**
     * 检索相似文本片段
     * @param query 查询文本
     * @param topK 检索Top K
     * @return 相似文本片段列表
     */
    public List<TextSegment> searchSimilar(String query, int topK) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        return vectorStore.search(queryEmbedding, topK).stream()
                .map(result -> result.textSegment())
                .collect(Collectors.toList());
    }
}
```

#### 2.3 RAG与AI模型服务模块
```java
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RAGQAService {
    private final ChatModel chatModel;
    private final FaissVectorStoreService vectorStoreService;

    @Value("${knowledge.top-k}")
    private int topK;

    // 构造函数注入ChatModel（OpenAI）
    public RAGQAService(@Value("${ai.openai.api-key}") String apiKey,
                        @Value("${ai.openai.model-name}") String modelName,
                        @Value("${ai.openai.timeout}") int timeout,
                        FaissVectorStoreService vectorStoreService) {
        // 初始化OpenAI ChatModel
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(timeout)
                .temperature(0.6)
                .build();
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * 知识库问答核心方法
     * @param userQuery 用户查询
     * @return AI生成的答案
     */
    public String answer(String userQuery) {
        // 1. 检索相似文本片段
        List<TextSegment> similarSegments = vectorStoreService.searchSimilar(userQuery, topK);
        String context = similarSegments.stream()
                .map(segment -> "- " + segment.text() + "\n（来源：" + segment.metadata("filePath") + "）")
                .collect(Collectors.joining("\n"));

        // 2. 构建Prompt（结合检索上下文）
        String prompt = String.format("""
                你是一个基于本地知识库的问答助手，仅根据以下提供的文档片段回答用户问题，不编造信息。
                若文档片段中没有相关信息，直接回答"抱歉，知识库中未找到相关信息"。
                
                文档片段：
                %s
                
                用户问题：%s
                """, context, userQuery);

        // 3. 调用AI模型生成答案
        return chatModel.generate(prompt).content();
    }
}
```

#### 2.4 接口层（Controller）
```java
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class QAController {
    private final RAGQAService ragQAService;

    public QAController(RAGQAService ragQAService) {
        this.ragQAService = ragQAService;
    }

    /**
     * 问答接口（接收JSON格式的用户查询）
     * 请求体：{"query": "Java如何集成OpenAI？"}
     * 响应体：{"answer": "Java集成OpenAI的核心步骤..."}
     */
    @PostMapping("/api/qa")
    public ResponseEntity<Map<String, String>> qa(@RequestBody Map<String, String> request) {
        try {
            String query = request.get("query");
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "查询不能为空"));
            }
            String answer = ragQAService.answer(query);
            return ResponseEntity.ok(Map.of("answer", answer));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "问答服务异常：" + e.getMessage()));
        }
    }
}
```

#### 2.5 启动类
```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KnowledgeQaSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeQaSystemApplication.class, args);
    }
}
```

### 3. 步骤3：项目测试
#### 3.1 本地测试（Postman调用接口）
- 请求URL：`http://localhost:8080/api/qa`
- 请求方法：POST
- 请求体（JSON）：
  ```json
  {
      "query": "Java集成OpenAI需要哪些依赖？"
  }
  ```
- 预期响应：
  ```json
  {
      "answer": "根据知识库片段，Java集成OpenAI需引入LangChain4j的OpenAI依赖，Maven配置如下：\n<dependency>\n    <groupId>dev.langchain4j</groupId>\n    <artifactId>langchain4j-openai</artifactId>\n    <version>0.24.0</version>\n</dependency>\n（来源：D:/knowledge-base/java-ai.pdf）"
  }
  ```

#### 3.2 前端集成（可选，Vue 3示例）
```vue
<template>
    <div class="qa-container">
        <h1>Java AI知识库问答系统</h1>
        <textarea v-model="query" placeholder="请输入你的问题..." rows="3"></textarea>
        <button @click="getAnswer">获取答案</button>
        <div class="answer" v-if="answer">{{ answer }}</div>
    </div>
</template>

<script setup>
import { ref } from 'vue';
import axios from 'axios';

const query = ref('');
const answer = ref('');

const getAnswer = async () => {
    if (!query.value.trim()) {
        alert('请输入查询内容');
        return;
    }
    try {
        const response = await axios.post('http://localhost:8080/api/qa', {
            query: query.value
        });
        answer.value = response.data.answer;
    } catch (error) {
        answer.value = '获取答案失败：' + (error.response?.data?.error || error.message);
    }
};
</script>

<style scoped>
.qa-container {
    max-width: 800px;
    margin: 20px auto;
    padding: 20px;
}
textarea {
    width: 100%;
    padding: 10px;
    margin-bottom: 10px;
    border: 1px solid #ddd;
    border-radius: 4px;
}
button {
    padding: 8px 16px;
    background: #0071e3;
    color: white;
    border: none;
    border-radius: 4px;
    cursor: pointer;
}
.answer {
    margin-top: 20px;
    padding: 10px;
    border: 1px solid #ddd;
    border-radius: 4px;
    white-space: pre-wrap;
}
</style>
```

### 4. 步骤4：Docker部署
#### 4.1 创建`Dockerfile`
```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/knowledge-qa-system-1.0-SNAPSHOT.jar app.jar
# 挂载知识库目录（外部目录映射到容器内）
VOLUME /app/knowledge-base
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--knowledge.path=/app/knowledge-base"]
```

#### 4.2 构建与运行Docker镜像
```bash
# 1. 打包Maven项目
mvn clean package -DskipTests

# 2. 构建Docker镜像
docker build -t java-ai-qa:1.0 .

# 3. 运行Docker容器（映射本地知识库目录D:/knowledge-base到容器内）
docker run -d -p 8080:8080 -v D:/knowledge-base:/app/knowledge-base --name java-ai-qa java-ai-qa:1.0
```


## 四、项目优化方向
1. 知识库增量更新：支持新增文档时自动加载、分割、生成向量（无需重启项目）
2. 多模型适配：支持切换本地模型（如Llama）与云模型，降低API成本
3. 权限控制：添加用户认证（如JWT），限制知识库访问权限
4. 日志与监控：集成ELK日志系统、Prometheus监控，追踪接口调用与模型性能


## 五、常见问题（FAQ）
1. Q：项目启动时FAISS向量库初始化失败如何解决？  
   A：检查知识库路径是否正确（`application.yml`的`knowledge.path`），确保路径下有可读取的PDF/TXT文件，且JVM内存足够（本地知识库大时需配置`-Xmx8g`）。
2. Q：如何支持更大规模的知识库（如10GB文档）？  
   A：替换FAISS为分布式向量数据库（如Milvus），支持分片存储与分布式检索，提升性能。


## 六、扩展资源
- 项目源码参考：[LangChain4j RAG示例](https://github.com/langchain4j/langchain4j-examples)
- Docker部署文档：[Spring Boot Docker部署指南](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#container-images)
- Vue 3前端文档：[Vue 3官方指南](https://vuejs.org/guide/introduction.html)