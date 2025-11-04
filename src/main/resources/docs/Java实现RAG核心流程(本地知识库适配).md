## 一、文档概述
本文档聚焦Java实现RAG（检索增强生成）的核心流程，涵盖文档加载、文本分割、向量生成与存储、检索匹配、模型生成五大环节，适配本地知识库场景（如PDF/TXT文档问答）。


## 二、RAG核心流程与Java实现
### 1. RAG整体架构
```
本地知识库（PDF/TXT）→ 文档加载 → 文本分割 → 向量生成 → 向量存储 → 检索匹配 → 结合Prompt → AI模型生成 → 输出答案
```

### 2. 环节1：文档加载（支持PDF/TXT/Word）
#### 2.1 依赖配置（Maven）
```xml
<!-- PDF加载：Apache PDFBox -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>2.0.32</version>
</dependency>
<!-- TXT加载：commons-io -->
<dependency>
    <groupId>commons-io</groupId>
    <artifactId>commons-io</artifactId>
    <version>2.15.0</version>
</dependency>
```

#### 2.2 核心代码（PDF/TXT加载工具类）
```java
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.commons.io.FileUtils;
import java.io.File;
import java.io.IOException;

public class DocumentLoader {
    /**
     * 加载PDF文档
     * @param filePath PDF文件路径
     * @return 文档文本内容
     */
    public static String loadPdf(String filePath) throws IOException {
        try (PDDocument document = PDDocument.load(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1); // 起始页
            stripper.setEndPage(document.getNumberOfPages()); // 结束页（全部）
            return stripper.getText(document);
        }
    }

    /**
     * 加载TXT文档
     * @param filePath TXT文件路径
     * @return 文档文本内容
     */
    public static String loadTxt(String filePath) throws IOException {
        File file = new File(filePath);
        return FileUtils.readFileToString(file, "UTF-8");
    }

    // 测试
    public static void main(String[] args) throws IOException {
        String pdfText = loadPdf("D:/knowledge/java-ai.pdf");
        System.out.println("PDF内容长度：" + pdfText.length());

        String txtText = loadTxt("D:/knowledge/rag-notes.txt");
        System.out.println("TXT内容前100字：" + txtText.substring(0, 100));
    }
}
```

### 3. 环节2：文本分割（解决LLM上下文窗口限制）
#### 3.1 核心逻辑
- 分割策略：按固定长度（如500字符）分割，保留上下文重叠（如重叠50字符，避免语义断裂）
- 适用场景：长文档（如PDF手册、技术文档）分割为小片段，适配LLM上下文窗口（如GPT-3.5支持4096 token）

#### 3.2 核心代码（文本分割工具类）
```java
import java.util.ArrayList;
import java.util.List;

public class TextSplitter {
    private final int chunkSize; // 每个片段的字符数
    private final int chunkOverlap; // 片段间重叠字符数

    // 构造函数：默认500字符片段，50字符重叠
    public TextSplitter() {
        this(500, 50);
    }

    public TextSplitter(int chunkSize, int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    /**
     * 分割文本
     * @param text 待分割的完整文本
     * @return 分割后的文本片段列表
     */
    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int textLength = text.length();

        while (start < textLength) {
            int end = Math.min(start + chunkSize, textLength);
            // 截取片段
            String chunk = text.substring(start, end);
            chunks.add(chunk);
            // 更新起始位置（减去重叠长度，避免语义断裂）
            start = end - chunkOverlap;
            // 防止重叠导致无限循环（如文本长度小于重叠长度）
            if (start >= end) break;
        }
        return chunks;
    }

    // 测试
    public static void main(String[] args) {
        TextSplitter splitter = new TextSplitter(200, 30);
        String longText = "Java集成AI大模型的核心步骤包括：1. 依赖配置；2. 模型实例化；3. Prompt构建；4. 响应解析；5. 结果处理。其中，依赖配置需根据模型类型（云模型/本地模型）选择对应的Maven依赖，如OpenAI需引入langchain4j-openai，本地Llama需引入llama-cpp-java...";
        List<String> chunks = splitter.split(longText);
        System.out.println("分割后片段数：" + chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            System.out.println("片段" + (i+1) + "：" + chunks.get(i));
        }
    }
}
```

### 4. 环节3：向量生成与存储（用FAISS/Redis）
#### 4.1 向量生成（基于Sentence-BERT）
##### 4.1.1 依赖配置
```xml
<!-- Sentence-BERT（生成文本向量） -->
<dependency>
    <groupId>de.tudarmstadt.ukp.dkpro.core</groupId>
    <artifactId>de.tudarmstadt.ukp.dkpro.core.stsb_bert</artifactId>
    <version>1.10.0</version>
</dependency>
<!-- 向量工具类 -->
<dependency>
    <groupId>org.nd4j</groupId>
    <artifactId>nd4j-native-platform</artifactId>
    <version>1.0.0-M2.1</version>
</dependency>
```

##### 4.1.2 核心代码（文本转向量）
```java
import de.tudarmstadt.ukp.dkpro.core.stsb_bert.STSBERTVectorizer;
import org.nd4j.linalg.api.ndarray.INDArray;

public class TextVectorizer {
    private final STSBERTVectorizer vectorizer;

    public TextVectorizer() {
        // 初始化Sentence-BERT模型（生成768维向量）
        this.vectorizer = new STSBERTVectorizer("sentence-transformers/paraphrase-MiniLM-L6-v2");
    }

    /**
     * 文本转向量
     * @param text 文本片段
     * @return 768维向量（转为double数组）
     */
    public double[] textToVector(String text) {
        INDArray vector = vectorizer.vectorize(text);
        return vector.toDoubleVector();
    }

    // 测试
    public static void main(String[] args) {
        TextVectorizer vectorizer = new TextVectorizer();
        String text = "Java集成OpenAI的核心依赖是langchain4j-openai";
        double[] vector = vectorizer.textToVector(text);
        System.out.println("向量维度：" + vector.length); // 输出768
        System.out.println("向量前5个值：" + vector[0] + "," + vector[1] + "," + vector[2] + "," + vector[3] + "," + vector[4]);
    }
}
```

#### 4.2 向量存储（用Redis）
##### 4.2.1 依赖配置
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
    <version>3.2.5</version>
</dependency>
<!-- Redis向量插件依赖（Redis 7.0+支持） -->
<dependency>
    <groupId>com.redis</groupId>
    <artifactId>redis-om-spring</artifactId>
    <version>0.8.3</version>
</dependency>
```

##### 4.2.2 核心代码（向量存储与检索）
```java
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.Set;

@Component
public class RedisVectorStore {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    private static final String VECTOR_KEY = "rag:document:vectors"; // Redis键

    /**
     * 存储向量（文本片段+向量）
     * @param docId 文档片段ID（唯一标识）
     * @param vector 文本向量
     * @param text 文本片段内容（存储为附加信息）
     */
    public void addVector(String docId, double[] vector, String text) {
        // 1. 存储向量（Redis ZSet：score=向量相似度，value=docId）
        // 注：实际需计算向量与查询向量的相似度（如余弦相似度），此处简化存储
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        zSetOps.add(VECTOR_KEY, docId, 0.0); // 临时score，检索时更新

        // 2. 存储文档片段内容（Redis Hash：key=docId，value=text）
        redisTemplate.opsForHash().put("rag:document:texts", docId, text);

        // 3. 存储向量（Redis Hash：key=docId，value=向量数组）
        redisTemplate.opsForHash().put("rag:document:vectorData", docId, vector);
    }

    /**
     * 检索相似向量（简化版：按docId查询，实际需计算余弦相似度）
     * @param queryVector 查询向量
     * @param topK 返回Top K个相似片段
     * @return 相似文本片段列表
     */
    public List<String> searchSimilar(double[] queryVector, int topK) {
        // 1. 获取所有向量数据（实际需遍历计算与queryVector的余弦相似度）
        Set<Object> docIds = redisTemplate.opsForHash().keys("rag:document:vectorData");
        List<String> similarTexts = new ArrayList<>();

        // 2. 简化逻辑：取前topK个文档片段（实际需按相似度排序）
        for (Object docId : docIds) {
            if (similarTexts.size() >= topK) break;
            String text = (String) redisTemplate.opsForHash().get("rag:document:texts", docId);
            similarTexts.add(text);
        }
        return similarTexts;
    }
}
```

### 5. 环节4：RAG完整流程串联
```java
import java.io.IOException;
import java.util.List;

public class RAGPipeline {
    public static void main(String[] args) throws IOException {
        // 1. 加载文档（PDF）
        String pdfText = DocumentLoader.loadPdf("D:/knowledge/java-ai.pdf");

        // 2. 文本分割
        TextSplitter splitter = new TextSplitter(500, 50);
        List<String> textChunks = splitter.split(pdfText);
        System.out.println("分割后片段数：" + textChunks.size());

        // 3. 向量生成与存储（Redis）
        TextVectorizer vectorizer = new TextVectorizer();
        RedisVectorStore vectorStore = new RedisVectorStore(); // 实际需Spring注入
        for (int i = 0; i < textChunks.size(); i++) {
            String chunk = textChunks.get(i);
            String docId = "chunk-" + i; // 生成唯一docId
            double[] vector = vectorizer.textToVector(chunk);
            vectorStore.addVector(docId, vector, chunk);
        }
        System.out.println("向量存储完成");

        // 4. 用户查询与检索
        String userQuery = "Java如何集成本地Llama模型？";
        double[] queryVector = vectorizer.textToVector(userQuery);
        List<String> similarChunks = vectorStore.searchSimilar(queryVector, 3); // 取Top3相似片段

        // 5. 构建Prompt（结合检索结果）
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("基于以下文档片段，回答问题：").append(userQuery).append("\n");
        promptBuilder.append("文档片段：\n");
        for (String chunk : similarChunks) {
            promptBuilder.append("- ").append(chunk).append("\n");
        }
        String finalPrompt = promptBuilder.toString();
        System.out.println("最终Prompt：" + finalPrompt);

        // 6. 调用AI模型生成答案（如本地Llama或OpenAI）
        // 此处省略模型调用代码，参考文档2中的模型集成逻辑
    }
}
```


## 三、实践优化建议
1. 文本分割优化：按语义分割（如段落、标题），而非纯字符长度，可使用`LangChain4j`的`RecursiveCharacterTextSplitter`
2. 向量检索优化：使用专业向量数据库（如FAISS、Milvus）替代Redis，支持高效余弦相似度计算（Redis适合小规模向量存储）
3. 缓存策略：缓存频繁查询的向量与检索结果，减少重复向量生成与存储开销


## 四、常见问题（FAQ）
1. Q：Sentence-BERT生成的向量维度可以调整吗？  
   A：默认生成768维向量，可更换模型（如`sentence-transformers/all-MiniLM-L6-v2`生成384维向量），维度越低，存储与检索效率越高，但语义精度可能下降。
2. Q：如何处理多语言文档的RAG？  
   A：使用支持多语言的向量模型（如`sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`），确保中文、英文等文本的向量生成语义一致。


## 五、扩展资源
- Sentence-BERT模型库：[Hugging Face Sentence-BERT](https://huggingface.co/sentence-transformers)
- FAISS Java集成：[FAISS-Java](https://github.com/facebookresearch/faiss/tree/main/java)
- Redis向量存储文档：[Redis Stack Vector Search](https://redis.io/docs/stack/search/reference/vectors/)
