#!/bin/bash
# Knowledge Repository 客户端 SDK 脚本
# 用法: ./scripts/client.sh [info|example|generate]

cd "$(dirname "$0")/.." || exit 1

echo "=========================================="
echo "  Knowledge Repository 客户端 SDK"
echo "=========================================="
echo ""

ACTION=${1:-info}

case "$ACTION" in
    info)
        echo "=== 客户端信息 ==="
        echo ""
        echo "API 基础地址: http://localhost:8091"
        echo ""
        echo "认证方式: Basic Auth (开发阶段)"
        echo "  用户名: admin"
        echo "  密码: admin"
        echo ""
        echo "内容类型: application/json"
        echo ""
        echo "使用 ./scripts/api.sh 进行 API 调用"
        ;;

    example)
        echo "=== 客户端示例代码 ==="
        echo ""
        
        LANG=${2:-python}
        
        case "$LANG" in
            python|py)
                cat << 'EOF'
# Python 客户端示例
import requests

BASE_URL = "http://localhost:8091"
AUTH = ("admin", "admin")

# 搜索知识库
def search(query, top_k=5):
    response = requests.post(
        f"{BASE_URL}/api/knowledge/search",
        json={"query": query, "topK": top_k},
        auth=AUTH
    )
    return response.json()

# 获取文档列表
def list_documents():
    response = requests.get(
        f"{BASE_URL}/api/admin/document/list",
        auth=AUTH
    )
    return response.json()

# 上传文档
def upload_document(file_path, owner_id="admin"):
    with open(file_path, "rb") as f:
        files = {"file": f}
        data = {"ownerId": owner_id, "departmentId": "default"}
        response = requests.post(
            f"{BASE_URL}/api/document/upload",
            files=files,
            data=data,
            auth=AUTH
        )
    return response.json()

# 使用示例
if __name__ == "__main__":
    # 搜索
    results = search("机器学习")
    for r in results.get("results", []):
        print(f"[{r['score']:.2f}] {r['content'][:100]}...")
EOF
                ;;

            java)
                cat << 'EOF'
// Java 客户端示例
import java.net.http.*;
import java.net.URI;

public class KnowledgeClient {
    private static final String BASE_URL = "http://localhost:8091";
    private static final String AUTH = "admin:admin";
    
    private final HttpClient client = HttpClient.newHttpClient();
    
    // 搜索知识库
    public String search(String query, int topK) throws Exception {
        String json = String.format(
            "{\"query\": \"%s\", \"topK\": %d}", query, topK
        );
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/knowledge/search"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Basic " + 
                java.util.Base64.getEncoder().encodeToString(AUTH.getBytes()))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        
        HttpResponse<String> response = client.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        return response.body();
    }
    
    public static void main(String[] args) throws Exception {
        KnowledgeClient client = new KnowledgeClient();
        System.out.println(client.search("机器学习", 5));
    }
}
EOF
                ;;

            curl)
                cat << 'EOF'
# cURL 客户端示例

# 搜索知识库
curl -X POST http://localhost:8091/api/knowledge/search \
  -H "Content-Type: application/json" \
  -u admin:admin \
  -d '{"query": "机器学习", "topK": 5}'

# 获取文档列表
curl http://localhost:8091/api/admin/document/list \
  -u admin:admin

# 获取统计信息
curl http://localhost:8091/api/admin/document/stats \
  -u admin:admin

# 上传文档
curl -X POST http://localhost:8091/api/document/upload \
  -u admin:admin \
  -F "file=@document.pdf" \
  -F "ownerId=admin" \
  -F "departmentId=default"
EOF
                ;;

            *)
                echo "不支持的语言: $LANG"
                echo "支持: python, java, curl"
                exit 1
                ;;
        esac
        ;;

    generate)
        echo "=== 生成客户端代码 ==="
        echo ""
        
        LANG=${2:-python}
        OUTPUT=${3:-"./data/client"}
        
        mkdir -p "$OUTPUT"
        
        case "$LANG" in
            python|py)
                $0 example python > "$OUTPUT/knowledge_client.py"
                echo "Python 客户端已生成: $OUTPUT/knowledge_client.py"
                ;;

            java)
                $0 example java > "$OUTPUT/KnowledgeClient.java"
                echo "Java 客户端已生成: $OUTPUT/KnowledgeClient.java"
                ;;

            *)
                echo "不支持的语言: $LANG"
                exit 1
                ;;
        esac
        ;;

    *)
        echo "用法: $0 [info|example|generate]"
        echo ""
        echo "  info                  - 客户端信息"
        echo "  example [语言]        - 示例代码 (python/java/curl)"
        echo "  generate [语言] [目录] - 生成客户端代码"
        exit 1
        ;;
esac
