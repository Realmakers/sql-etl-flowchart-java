package com.sqlflow.backend.service;

import com.sqlflow.backend.model.ParsedSQL;
import com.sqlflow.backend.model.ParsedSQL.*;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.select.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SqlParserService {

    public ParsedSQL parseSQL(String sql) {
        ParsedSQL result = new ParsedSQL();
        try {
            String cleanSql = removeComments(sql);
            List<Statement> statements = CCJSqlParserUtil.parseStatements(cleanSql).getStatements();

            List<String> knownNames = new ArrayList<>();
            List<SubQuery> subQueries = new ArrayList<>(); // Collector for subqueries

            for (Statement stmt : statements) {
                if (stmt instanceof CreateTable) {
                    CreateTable createTable = (CreateTable) stmt;
                    if (createTable.getSelect() != null) {
                        String tableName = createTable.getTable().getName();
                        SubQuery subQuery = processSelectBody(
                                createTable.getSelect(), 
                                "temp_node_" + tableName,
                                tableName,
                                false,
                                false,
                                true, // isTempTable
                                knownNames,
                                subQueries
                        );
                        result.getCtes().add(subQuery);
                        knownNames.add(tableName.toLowerCase());
                    }
                } else if (stmt instanceof Select) {
                    if (stmt instanceof Select) {
                        Select select = (Select) stmt;
                        if (select.getWithItemsList() != null) {
                            for (WithItem withItem : select.getWithItemsList()) {
                                String cteName = withItem.getName();
                                SubQuery cteQuery = processSelectBody(
                                        withItem.getSelect(),
                                        "cte_" + cteName,
                                        cteName,
                                        true,
                                        false,
                                        false,
                                        knownNames,
                                        subQueries
                                );
                                result.getCtes().add(cteQuery);
                                knownNames.add(cteName.toLowerCase());
                            }
                        }
                        
                        SubQuery mainQuery = processSelectBody(
                                select,
                                "main",
                                "最终查询",
                                false,
                                false,
                                false,
                                knownNames,
                                subQueries
                        );
                        result.setMainQuery(mainQuery);
                    }
                }
            }
            
            result.getAllQueries().addAll(result.getCtes());
            if (result.getMainQuery() != null) {
                result.getAllQueries().add(result.getMainQuery());
            }
            
            // Add collected nested subqueries
            result.getSubQueries().addAll(subQueries);
            result.getAllQueries().addAll(subQueries);

        } catch (JSQLParserException e) {
            e.printStackTrace();
        }
        return result;
    }

    private SubQuery processSelectBody(Select selectBody, String id, String name, 
                                       boolean isCTE, boolean isSubQuery, boolean isTempTable, 
                                       List<String> knownNames, List<SubQuery> subQueries) {
        SubQuery subQuery = SubQuery.builder()
                .id(id)
                .name(name)
                .isCTE(isCTE)
                .isSubQuery(isSubQuery)
                .isTempTable(isTempTable)
                .build();

        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;
            
            FromItem fromItem = plainSelect.getFromItem();
            if (fromItem != null) {
                processFromItem(fromItem, subQuery, knownNames, subQueries);
            }
            
            if (plainSelect.getJoins() != null) {
                for (Join join : plainSelect.getJoins()) {
                    processJoin(join, subQuery, knownNames, subQueries);
                }
            }
            
            if (plainSelect.getSelectItems() != null) {
                for (SelectItem item : plainSelect.getSelectItems()) {
                    processSelectItem(item, subQuery);
                }
            }
            
            if (plainSelect.getWhere() != null) {
                subQuery.getFilters().add(FilterInfo.builder()
                        .clause("WHERE")
                        .condition(plainSelect.getWhere().toString())
                        .build());
            }
            
            if (plainSelect.getGroupBy() != null) {
                subQuery.setGroupBy(plainSelect.getGroupBy().getGroupByExpressions().stream()
                        .map(Object::toString).collect(Collectors.toList()));
            }
            
            if (plainSelect.getOrderByElements() != null) {
                subQuery.setOrderBy(plainSelect.getOrderByElements().stream()
                        .map(Object::toString).collect(Collectors.toList()));
            }

        } else if (selectBody instanceof SetOperationList) {
            SetOperationList setOp = (SetOperationList) selectBody;
            UnionInfo unionInfo = UnionInfo.builder()
                    .type(setOp.getOperations().get(0).toString())
                    .build();
            
             for (Select branch : setOp.getSelects()) {
                 SubQuery branchQuery = processSelectBody(branch, id + "_branch", name, false, true, false, knownNames, subQueries);
                 subQuery.getTables().addAll(branchQuery.getTables());
                 subQuery.getDependsOn().addAll(branchQuery.getDependsOn());
                 
                 for (TableRef t : branchQuery.getTables()) {
                     if (!unionInfo.getSources().contains(t.getName())) {
                         unionInfo.getSources().add(t.getName());
                     }
                 }
             }
             subQuery.setUnionInfo(unionInfo);
        } else if (selectBody instanceof ParenthesedSelect) {
            ParenthesedSelect ps = (ParenthesedSelect) selectBody;
            if (ps.getSelect() != null) {
                return processSelectBody(ps.getSelect(), id, name, isCTE, isSubQuery, isTempTable, knownNames, subQueries);
            }
        }

        return subQuery;
    }

    private void processFromItem(FromItem fromItem, SubQuery subQuery, List<String> knownNames, List<SubQuery> subQueries) {
        if (fromItem instanceof Table) {
            Table table = (Table) fromItem;
            TableRef ref = createTableRef(table, knownNames);
            subQuery.getTables().add(ref);
            addDependency(subQuery, ref, knownNames);
        } else if (fromItem instanceof ParenthesedSelect) {
            ParenthesedSelect subSelect = (ParenthesedSelect) fromItem;
            // Generate unique ID for subquery
            String subId = "sub_" + UUID.randomUUID().toString().substring(0, 8);
            String subName = subSelect.getAlias() != null ? subSelect.getAlias().getName() : "subquery";
            
            // Recursively process
            SubQuery nestedSub = processSelectBody(subSelect.getSelect(), subId, subName, false, true, false, knownNames, subQueries);
            subQueries.add(nestedSub); // Add to global list
            
            // Add as table reference
            if (subSelect.getAlias() != null) {
                TableRef ref = TableRef.builder()
                        .name(subId) // Use ID as name to link
                        .tableName(subName)
                        .alias(subSelect.getAlias().getName())
                        .type("subquery")
                        .build();
                subQuery.getTables().add(ref);
                // Add dependency to this subquery
                if (!subQuery.getDependsOn().contains(subId)) {
                    subQuery.getDependsOn().add(subId);
                }
            }
        }
    }

    private void processJoin(Join join, SubQuery subQuery, List<String> knownNames, List<SubQuery> subQueries) {
        FromItem rightItem = join.getRightItem();
        TableRef ref = null;
        if (rightItem instanceof Table) {
            ref = createTableRef((Table) rightItem, knownNames);
            subQuery.getTables().add(ref);
            addDependency(subQuery, ref, knownNames);
        } else if (rightItem instanceof ParenthesedSelect) {
             ParenthesedSelect subSelect = (ParenthesedSelect) rightItem;
             // Generate unique ID for subquery
             String subId = "sub_" + UUID.randomUUID().toString().substring(0, 8);
             String subName = subSelect.getAlias() != null ? subSelect.getAlias().getName() : "subquery";
             
             // Recursively process
             SubQuery nestedSub = processSelectBody(subSelect.getSelect(), subId, subName, false, true, false, knownNames, subQueries);
             subQueries.add(nestedSub); // Add to global list

             if (subSelect.getAlias() != null) {
                ref = TableRef.builder()
                        .name(subId) // Use ID to link
                        .tableName(subName)
                        .alias(subSelect.getAlias().getName())
                        .type("subquery")
                        .build();
                subQuery.getTables().add(ref);
                
                // Add dependency
                if (!subQuery.getDependsOn().contains(subId)) {
                    subQuery.getDependsOn().add(subId);
                }
            }
        }

        if (ref != null) {
            String type = (join.isLeft() ? "LEFT" : join.isRight() ? "RIGHT" : join.isFull() ? "FULL" : join.isInner() ? "INNER" : "") + " JOIN";
            String condition = join.getOnExpression() != null ? join.getOnExpression().toString() : "";
            subQuery.getJoins().add(JoinInfo.builder()
                    .type(type)
                    .table(ref)
                    .condition(condition)
                    .build());
        }
    }

    private void processSelectItem(SelectItem item, SubQuery subQuery) {
        String expr = item.toString();
        String alias = "";
        String originalName = expr;
        
        try {
            if (item.getClass().getSimpleName().equals("SelectExpressionItem")) {
                // ...
            }
        } catch (Exception e) {}

        if (expr.equals("*")) {
             alias = "*";
        } else {
             if (item instanceof SelectExpressionItem) { 
                 SelectExpressionItem sei = (SelectExpressionItem) item;
                 Expression expression = sei.getExpression();
                 expr = expression.toString();
                 if (sei.getAlias() != null) {
                     alias = sei.getAlias().getName();
                     originalName = expr;
                 } else {
                     if (expression instanceof net.sf.jsqlparser.schema.Column) {
                         alias = ((net.sf.jsqlparser.schema.Column) expression).getColumnName();
                     }
                 }
             }
        }

        subQuery.getFields().add(FieldInfo.builder()
                .expression(expr)
                .alias(alias)
                .originalName(originalName)
                .displayText(alias.isEmpty() ? expr : expr + " AS " + alias)
                .transformation(detectTransformation(expr))
                .build());
    }

    private TableRef createTableRef(Table table, List<String> knownNames) {
        String fullName = table.getFullyQualifiedName();
        String alias = table.getAlias() != null ? table.getAlias().getName() : fullName;
        
        String type = "dimension";
        String lowerName = fullName.toLowerCase();
        
        boolean isKnown = knownNames.contains(lowerName);
        
        if (!isKnown) {
             if (lowerName.contains("app") || lowerName.contains("dm") || lowerName.contains("dwd") || lowerName.contains("dws")) {
                type = "fact";
            }
        } else {
            type = null;
        }

        return TableRef.builder()
                .name(fullName)
                .schema(table.getSchemaName())
                .tableName(table.getName())
                .alias(alias)
                .type(type)
                .build();
    }

    private void addDependency(SubQuery subQuery, TableRef ref, List<String> knownNames) {
        String lowerName = ref.getName().toLowerCase();
        if (knownNames.contains(lowerName)) {
            if (!subQuery.getDependsOn().contains(lowerName)) {
                subQuery.getDependsOn().add(lowerName);
            }
        }
    }

    private String detectTransformation(String expr) {
        String upper = expr.toUpperCase();
        if (upper.matches(".*\\b(SUM|COUNT|AVG|MAX|MIN|GROUP_CONCAT)\\s*\\(.*")) return "聚合:" + getFunc(upper);
        if (upper.contains("OVER") && upper.contains("(")) return "开窗函数";
        if (upper.matches(".*\\b(CASE)\\b.*")) return "条件判断(CASE)";
        if (upper.contains("JOIN") || upper.contains("CONCAT")) return "拼接";
        return "原始字段";
    }
    
    private String getFunc(String upper) {
        if (upper.contains("SUM")) return "SUM";
        if (upper.contains("COUNT")) return "COUNT";
        return "";
    }

    private String removeComments(String sql) {
        return sql.replaceAll("--.*$", "").replaceAll("/\\*[\\s\\S]*?\\*/", "");
    }
}
