package com.sqlflow.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedSQL {
    @Builder.Default
    private List<SubQuery> ctes = new ArrayList<>();
    private SubQuery mainQuery;
    @Builder.Default
    private List<SubQuery> subQueries = new ArrayList<>();
    @Builder.Default
    private List<SubQuery> allQueries = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubQuery {
        private String id;
        private String name;
        private boolean isCTE;
        private boolean isSubQuery;
        private boolean isTempTable;
        @Builder.Default
        private List<TableRef> tables = new ArrayList<>();
        @Builder.Default
        private List<FieldInfo> fields = new ArrayList<>();
        @Builder.Default
        private List<JoinInfo> joins = new ArrayList<>();
        @Builder.Default
        private List<FilterInfo> filters = new ArrayList<>();
        @Builder.Default
        private List<String> groupBy = new ArrayList<>();
        @Builder.Default
        private List<String> orderBy = new ArrayList<>();
        @Builder.Default
        private List<String> dependsOn = new ArrayList<>();
        private UnionInfo unionInfo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableRef {
        private String name;
        private String schema;
        private String tableName;
        private String alias;
        private String type; // 'fact' | 'dimension'
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldInfo {
        private String expression;
        private String alias;
        private String originalName;
        private String displayText;
        private String transformation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinInfo {
        private String type;
        private TableRef table;
        private String condition;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterInfo {
        private String clause;
        private String condition;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnionInfo {
        private String type; // 'UNION ALL' | 'UNION'
        @Builder.Default
        private List<String> sources = new ArrayList<>();
    }
}
