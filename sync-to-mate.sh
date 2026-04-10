#!/bin/bash
#
# Sync modules code to mate-campusclaw
# 用法: ./sync-to-mate.sh [模块名，可选，默认全部]
#

set -e

# 定义映射关系
# 格式: modules目录名:mate-campusclaw目录名:包前缀(点分隔):包路径(斜杠分隔)
MAPPINGS=(
    "agent-core:agent:com.campusclaw.agent:com/campusclaw/agent"
    "ai:ai:com.campusclaw.ai:com/campusclaw/ai"
    "coding-agent-cli:codingagent:com.campusclaw.codingagent:com/campusclaw/codingagent"
    "cron:cron:com.campusclaw.cron:com/campusclaw/cron"
    "tui:tui:com.campusclaw.tui:com/campusclaw/tui"
)

SOURCE_BASE="modules"
TARGET_BASE="mate-campusclaw/src/main/java/com/huawei/hicampus/mate/matecampusclaw"
TEST_TARGET_BASE="mate-campusclaw/src/test/java/com/huawei/hicampus/mate/matecampusclaw"

# 所有需要替换的包映射 (旧包名 -> 新包名)
# 使用数组代替关联数组，兼容旧版 bash
OLD_PACKAGES=(
    "com.campusclaw.agent"
    "com.campusclaw.ai"
    "com.campusclaw.codingagent"
    "com.campusclaw.cron"
    "com.campusclaw.tui"
)

NEW_PACKAGES=(
    "com.huawei.hicampus.mate.matecampusclaw.agent"
    "com.huawei.hicampus.mate.matecampusclaw.ai"
    "com.huawei.hicampus.mate.matecampusclaw.codingagent"
    "com.huawei.hicampus.mate.matecampusclaw.cron"
    "com.huawei.hicampus.mate.matecampusclaw.tui"
)

sync_module() {
    local module_name=$1
    local target_name=$2
    local pkg_prefix=$3
    local pkg_path=$4

    echo "=== Syncing $module_name -> $target_name ==="

    # 确定源目录
    local src_main="$SOURCE_BASE/$module_name/src/main/java/$pkg_path"
    local src_test="$SOURCE_BASE/$module_name/src/test/java/$pkg_path"

    # 确定目标目录
    local target_pkg="com.huawei.hicampus.mate.matecampusclaw.${target_name}"
    local target_main="$TARGET_BASE/$target_name"
    local target_test="$TEST_TARGET_BASE/$target_name"

    # 检查源目录是否存在
    if [[ ! -d "$src_main" ]]; then
        echo "Warning: Source main directory not found: $src_main"
        return 0
    fi

    # 清空目标目录
    rm -rf "$target_main"
    mkdir -p "$target_main"

    # 复制主代码
    cp -r "$src_main"/* "$target_main/" 2>/dev/null || true

    # 替换所有包名（包括跨模块的 import）
    replace_package_names "$target_main"

    # 处理测试代码
    if [[ -d "$src_test" ]]; then
        rm -rf "$target_test"
        mkdir -p "$target_test"
        cp -r "$src_test"/* "$target_test/" 2>/dev/null || true

        replace_package_names "$target_test"
    fi

    # 统计文件数
    local main_count=$(find "$target_main" -name "*.java" 2>/dev/null | wc -l)
    local test_count=0
    if [[ -d "$target_test" ]]; then
        test_count=$(find "$target_test" -name "*.java" 2>/dev/null | wc -l)
    fi

    echo "  ✓ Synced $module_name ($main_count main, $test_count test files)"
}

# 替换所有包名的函数
replace_package_names() {
    local target_dir=$1

    # 遍历所有包映射进行替换
    local count=${#OLD_PACKAGES[@]}
    for ((i=0; i<count; i++)); do
        local old_pkg="${OLD_PACKAGES[$i]}"
        local new_pkg="${NEW_PACKAGES[$i]}"

        # 替换 package 声明
        find "$target_dir" -name "*.java" -exec sed -i '' \
            "s/package $old_pkg/package $new_pkg/g" {} \; 2>/dev/null || true

        # 替换 import 语句
        find "$target_dir" -name "*.java" -exec sed -i '' \
            "s/import $old_pkg/import $new_pkg/g" {} \; 2>/dev/null || true

        # 替换完全限定类名（如 com.campusclaw.xxx.SomeClass.method()）
        # 注意：不使用 \b 单词边界，因为 macOS sed 不支持
        find "$target_dir" -name "*.java" -exec sed -i '' \
            "s/$old_pkg\./$new_pkg./g" {} \; 2>/dev/null || true

        # 替换字符串字面量中的包名（如 scanBasePackages = "com.campusclaw"）
        find "$target_dir" -name "*.java" -exec sed -i '' \
            "s/\"$old_pkg/\"$new_pkg/g" {} \; 2>/dev/null || true
    done

    # 特殊处理：替换根包 com.campusclaw（用于 scanBasePackages 等字符串）
    find "$target_dir" -name "*.java" -exec sed -i '' \
        's/"com\.campusclaw"/"com.huawei.hicampus.mate.matecampusclaw"/g' {} \; 2>/dev/null || true
    find "$target_dir" -name "*.java" -exec sed -i '' \
        's/"com\.campusclaw\./"com.huawei.hicampus.mate.matecampusclaw./g' {} \; 2>/dev/null || true
}

# 主逻辑
if [[ $# -eq 1 ]]; then
    # 同步指定模块
    for mapping in "${MAPPINGS[@]}"; do
        IFS=':' read -r module target pkg path <<< "$mapping"
        if [[ "$module" == "$1" ]]; then
            sync_module "$module" "$target" "$pkg" "$path"
            exit 0
        fi
    done
    echo "Error: Unknown module: $1"
    echo "Available: agent-core, ai, coding-agent-cli, cron, tui"
    exit 1
else
    # 同步所有模块
    echo "Syncing all modules to mate-campusclaw..."
    for mapping in "${MAPPINGS[@]}"; do
        IFS=':' read -r module target pkg path <<< "$mapping"
        sync_module "$module" "$target" "$pkg" "$path"
    done
    echo ""
    echo "=== Sync complete ==="

    # 验证是否还有残留的 com.campusclaw 引用
    echo ""
    echo "=== 验证结果 ==="
    remaining=$(grep -r "import com.campusclaw" /Users/simon/01.code/pi-mono-java/mate-campusclaw/src --include="*.java" 2>/dev/null | wc -l | tr -d ' ')
    if [[ "$remaining" == "0" ]]; then
        echo "✓ 所有包名已正确替换"
    else
        echo "⚠ 仍有 $remaining 处 com.campusclaw 引用"
        grep -r "import com.campusclaw" /Users/simon/01.code/pi-mono-java/mate-campusclaw/src --include="*.java" 2>/dev/null | head -5
    fi
fi
