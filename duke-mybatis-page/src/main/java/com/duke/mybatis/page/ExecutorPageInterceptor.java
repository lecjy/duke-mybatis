package com.duke.mybatis.page;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
})
class ExecutorPageInterceptor implements Interceptor {
    private final Logger log = LoggerFactory.getLogger(ExecutorPageInterceptor.class);

    private PageRequestHolder pageRequestHolder;

    public ExecutorPageInterceptor(PageRequestHolder pageRequestHolder) {
        this.pageRequestHolder = pageRequestHolder;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] queryArgs = invocation.getArgs();

        MappedStatement mappedStatement = (MappedStatement) queryArgs[0];
        Object parameter = queryArgs[1];
        RowBounds rowBounds = (RowBounds) queryArgs[2];
        Executor executor = (Executor) invocation.getTarget();
        CacheKey cacheKey;
        BoundSql boundSql;
        if (queryArgs.length == 4) {
            boundSql = mappedStatement.getBoundSql(parameter);
            cacheKey = executor.createCacheKey(mappedStatement, parameter, rowBounds, boundSql);
        } else {
            cacheKey = (CacheKey) queryArgs[4];
            boundSql = (BoundSql) queryArgs[5];
        }

        SqlCommandType sqlCommandType = mappedStatement.getSqlCommandType();
        if (!SqlCommandType.SELECT.name().equals(sqlCommandType.name())) {
            return invocation.proceed();
        }
        // 请求头中带有分页请求，如果一个接口中有多个sql，会造成这些SQL都分页了
//        PageRequest request = pageRequestHolder.pageRequest();
        PageRequest request = null;
        Object param = boundSql.getParameterObject();
        if (param instanceof Map) {
            Iterator<Map.Entry> entrySet = ((Map) param).entrySet().iterator();
            while (entrySet.hasNext()) {
                Entry<String, Object> e = (Entry) entrySet.next();
                // Mapper接口参数中带有分页请求
                if (e.getValue() instanceof PageRequest) {
                    request = (PageRequest) e.getValue();
                    entrySet.remove();
                }
            }
        } else if (param instanceof PageRequest) {
            request = (PageRequest) param;
        }
        if (request != null) {
            Page<?> page = Page.builder().build();
            if (Page.class.getName().equals(mappedStatement.getConfiguration().getResultMap(((ResultMap) ((List) mappedStatement.getResultMaps()).get(0)).getId()).getType().getName())) {
                pageRequestHolder.page(page);
            }
            String originSql = boundSql.getSql().replaceAll("\\s+", " ");
            log.warn("original sql ===> " + originSql);
            int offset = request.getPage() > 0 ? (request.getPage() - 1) * request.getSize() : 0;
            if (request.isCounting()) {
                // 必须先执行PermissionInterceptor添加查询条件，之后PageInterceptor执行时，才是正确的sql，问题是PageInterceptor查询总数时，由于ParameterHandler添加参数时，PageInterceptor(StatementHandler类型)的queryTotal已经执行，且执行的是未添加参数的sql，所以报错
                page.setTotalRecord(this.queryTotal(mappedStatement, boundSql.getParameterMappings(), boundSql.getParameterObject(), originSql));
            }
            Configuration configuration = mappedStatement.getConfiguration();
            MappedStatement newMappedStatement = copyFromMappedStatement(mappedStatement, new BoundSqlSource(boundSql));
            MetaObject metaObject = MetaObject.forObject(newMappedStatement, configuration.getObjectFactory(), configuration.getObjectWrapperFactory(), configuration.getReflectorFactory());
            String pageSql = this.buildPageSql(originSql, offset, request.getSize());
            metaObject.setValue("sqlSource.boundSql.sql", pageSql);
            log.warn("page     sql ===> " + pageSql);
            queryArgs[0] = newMappedStatement;
        }
        return invocation.proceed();
    }

    private long queryTotal(MappedStatement mappedStatement, List<ParameterMapping> parameterMappings, Object parameterObject, String originSql) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        String countSql = "";
        long total = 0;
        try {
            countSql = this.buildCountSql(originSql);
            connection = mappedStatement.getConfiguration().getEnvironment().getDataSource().getConnection();
            //Connection connection = (Connection) invocation.getArgs()[0];
            statement = connection.prepareStatement(countSql);
            BoundSql countBoundSql = new BoundSql(mappedStatement.getConfiguration(), countSql, parameterMappings, parameterObject);
            new DefaultParameterHandler(mappedStatement, countBoundSql.getParameterObject(), countBoundSql).setParameters(statement);
            resultSet = statement.executeQuery();
            log.warn("count    sql ===> " + countSql);
            if (resultSet.next()) {
                total = resultSet.getLong(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Execption when execute sql " + countSql + ", because of " + e.getMessage());
        } finally {
            try {
                if (resultSet != null) {
                    resultSet.close();
                }
                if (statement != null) {
                    statement.close();
                }
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
                log.warn("Execption when close resetSet or statement, because of " + e.getMessage());
            }
        }
        return total;
    }

    private String buildPageSql(String originSql, int offset, int limit) {
        return originSql + " limit " + offset + ", " + limit;
    }

    private String buildCountSql(String sql) {
        int formIndex = indexOfFrom(0, sql);
        sql = sql.substring(formIndex);
        int indexOfOrderBy;
        if ((indexOfOrderBy = sql.indexOf("order by")) != -1) {
            sql = sql.substring(0, indexOfOrderBy);
        }
        return "select count(1) as total " + sql;
    }

    public static int indexOfFrom(int start, String sql) {
        int count = 0;//括号的数量
        int fromIndex = sql.indexOf("from", start);

        //搜索位置
        int index = fromIndex;
        int bIndex = -1;
        while ((bIndex = sql.lastIndexOf("(", index)) != -1) {
            count++;
            index = bIndex - 1;
        }
        //搜索反括号
        index = fromIndex;
        int eIndex = -1;
        while ((eIndex = sql.lastIndexOf(")", index)) != -1) {
            count--;
            index = eIndex - 1;
        }

        if (count == 0) {
            return fromIndex;
        } else {
            return indexOfFrom(fromIndex + 1, sql);
        }
    }

    private MappedStatement copyFromMappedStatement(MappedStatement ms, SqlSource newSqlSource) {
        MappedStatement.Builder builder = new MappedStatement.Builder(ms.getConfiguration(), ms.getId(), newSqlSource, ms.getSqlCommandType());
        builder.resource(ms.getResource());
        builder.fetchSize(ms.getFetchSize());
        builder.statementType(ms.getStatementType());
        builder.keyGenerator(ms.getKeyGenerator());
        if (ms.getKeyProperties() != null && ms.getKeyProperties().length != 0) {
            StringBuilder keyProperties = new StringBuilder();
            for (String keyProperty : ms.getKeyProperties()) {
                keyProperties.append(keyProperty).append(",");
            }
            keyProperties.delete(keyProperties.length() - 1, keyProperties.length());
            builder.keyProperty(keyProperties.toString());
        }
        builder.timeout(ms.getTimeout());
        builder.parameterMap(ms.getParameterMap());
        builder.resultMaps(ms.getResultMaps());
        builder.resultSetType(ms.getResultSetType());
        builder.cache(ms.getCache());
        builder.flushCacheRequired(ms.isFlushCacheRequired());
        builder.useCache(ms.isUseCache());
        return builder.build();
    }
}