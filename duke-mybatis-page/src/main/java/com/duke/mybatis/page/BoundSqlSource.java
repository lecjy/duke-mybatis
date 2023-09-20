package com.duke.mybatis.page;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;

@EnableMybatisPage
public class BoundSqlSource implements SqlSource {
    private BoundSql boundSql;

    public BoundSqlSource(BoundSql boundSql) {
        this.boundSql = boundSql;
    }

    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        return boundSql;
    }
}
