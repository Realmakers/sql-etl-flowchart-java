#!/bin/bash
set -e

# 进入脚本所在目录
cd "$(dirname "$0")"

# 检查是否已安装 mvn
if ! command -v mvn &> /dev/null; then
    echo "⚠️  未检测到全局 Maven 环境"
    
    # 检查本地是否已下载 Maven
    MAVEN_VERSION="3.9.6"
    MAVEN_DIR="apache-maven-$MAVEN_VERSION"
    
    if [ ! -d "$MAVEN_DIR" ]; then
        echo "⬇️  正在下载 Maven $MAVEN_VERSION ..."
        curl -L -O "https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz"
        
        echo "📦 解压 Maven..."
        tar -xzf "apache-maven-$MAVEN_VERSION-bin.tar.gz"
        rm "apache-maven-$MAVEN_VERSION-bin.tar.gz"
    fi
    
    # 设置 PATH
    export PATH="$PWD/$MAVEN_DIR/bin:$PATH"
    echo "✅ 使用本地 Maven: $(mvn -v | head -n 1)"
else
    echo "✅ 检测到全局 Maven: $(mvn -v | head -n 1)"
fi

echo "🚀 正在启动 Java 后端服务..."
mvn spring-boot:run
