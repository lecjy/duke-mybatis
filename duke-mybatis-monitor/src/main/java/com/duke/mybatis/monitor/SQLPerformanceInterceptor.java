package com.duke.mybatis.monitor;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Statement;
import java.util.List;

@Intercepts({
        @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "queryCursor", args = {Statement.class}),
        @Signature(type = StatementHandler.class, method = "update", args = {Statement.class})
})
public class SQLPerformanceInterceptor implements Interceptor {
    private Logger log = LoggerFactory.getLogger(SQLPerformanceInterceptor.class);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object target = invocation.getTarget();
        //获取非代理的目标对象
        MetaObject meta = SystemMetaObject.forObject(target);
        while (meta.hasGetter("h")) {
            target = meta.getValue("h.target");
            meta = SystemMetaObject.forObject(target);
        }
        StatementHandler statementHandler = (StatementHandler) target;
        //获得当前执行的SQL语句
        MetaObject statmentObject = SystemMetaObject.forObject(statementHandler);
        //获取全局配置对象
        Configuration configuration = (Configuration) statmentObject.getValue("delegate.configuration");

        //获得需要编译的sql语句
        BoundSql boundSql = (BoundSql) statmentObject.getValue("delegate.boundSql");
        //获得sql
        String sql = boundSql.getSql().toLowerCase().trim().replace("\n", "");

        //获取所有的参数
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        Object parameterObject = boundSql.getParameterObject();
        TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
        String sqlWithOutPlaceholder = sql;
        if (parameterMappings != null && parameterMappings.size() > 0) {
            for (int i = 0; i < parameterMappings.size(); i++) {
                ParameterMapping parameterMapping = parameterMappings.get(i);
                if (parameterMapping.getMode() != ParameterMode.OUT) {
                    Object value;
                    String propertyName = parameterMapping.getProperty();
                    if (boundSql.hasAdditionalParameter(propertyName)) { // issue #448 ask first for additional params
                        value = boundSql.getAdditionalParameter(propertyName);
                    } else if (parameterObject == null) {
                        value = null;
                    } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                        value = parameterObject;
                    } else {
                        MetaObject metaObject = configuration.newMetaObject(parameterObject);
                        value = metaObject.getValue(propertyName);
                    }
                    sqlWithOutPlaceholder = sqlWithOutPlaceholder.replaceFirst("\\?", value.toString());
                }
            }
        }
        Object statement = statmentObject.getValue("delegate.mappedStatement");
        String statementId = "";
        if (statement instanceof MappedStatement) {
            statementId = ((MappedStatement) statement).getId();
        }
        long beginTime = System.currentTimeMillis();
        Object result = invocation.proceed();
        log.warn("it cost [ " + new BigDecimal(System.currentTimeMillis() - beginTime).doubleValue() + "ms ] to execute ===> [" + statementId + " ] [ " + sqlWithOutPlaceholder + "]");
        return result;
    }
}
