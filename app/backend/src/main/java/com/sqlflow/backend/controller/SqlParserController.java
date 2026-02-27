package com.sqlflow.backend.controller;

import com.sqlflow.backend.model.ParsedSQL;
import com.sqlflow.backend.service.SqlParserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SqlParserController {

    private final SqlParserService sqlParserService;

    @PostMapping("/parse")
    public ParsedSQL parse(@RequestBody String sql) {
        // Handle JSON string wrapping if necessary
        String cleanSql = sql;
        if (cleanSql.startsWith("\"") && cleanSql.endsWith("\"")) {
            cleanSql = cleanSql.substring(1, cleanSql.length() - 1)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\t", "\t");
        }
        return sqlParserService.parseSQL(cleanSql);
    }
}
