package com.duke.mybatis.page;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

@Intercepts({
        @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class})
})
public class StatementPageInterceptor implements Interceptor {
    private final Logger log = LoggerFactory.getLogger(StatementPageInterceptor.class);
    private static final Pattern PATTERN = Pattern.compile("(\\w+\\.)?deleted *= *((false)|0)", Pattern.CASE_INSENSITIVE);
    private PageRequestHolder pageRequestHolder;

    public StatementPageInterceptor(PageRequestHolder pageRequestHolder) {
        this.pageRequestHolder = pageRequestHolder;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = (StatementHandler) parseTarget(invocation.getTarget());
        // 通过MetaObject访问对象的属性
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
        // 获取成员变量mappedStatement
        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        BoundSql boundSql = (BoundSql) metaObject.getValue("delegate.boundSql");

        SqlCommandType sqlCommandType = mappedStatement.getSqlCommandType();
        if (!SqlCommandType.SELECT.name().equals(sqlCommandType.name())) {
            return invocation.proceed();
        }
        String id = mappedStatement.getId();
        PageRequest request = null;
        // 请求头中带有分页请求，如果一个接口中有多个sql，会造成这些SQL都分页了，借助Pageable注解，只对其中一条SQL分页，但无法避免共用的SQL被分页的情况
//        Map<String, ? extends Class<?>> mappers = mappedStatement.getConfiguration().getMapperRegistry().getMappers().stream().collect(Collectors.toMap(item -> item.getName(), item -> item));
//        Method[] declaredMethods = mappers.get(mappedStatement.getId().substring(0, mappedStatement.getId().lastIndexOf("."))).getDeclaredMethods();
//        Method method = Arrays.stream(declaredMethods).collect(Collectors.toMap(item -> item.getName(), item -> item)).get(mappedStatement.getId().substring(mappedStatement.getId().lastIndexOf(".") + 1));
//        Pageable pageable = method.getDeclaredAnnotation(Pageable.class);
//        if (pageable != null) {
//            request = pageRequestHolder.pageRequest();
//        }
        Object param = boundSql.getParameterObject();
        if (param instanceof Map) {
            Iterator<Map.Entry<String, Object>> entrySet = ((Map) param).entrySet().iterator();
            while (entrySet.hasNext()) {
                Entry<String, Object> e = entrySet.next();
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
            // 这里不能取mappedStatement.getResultMaps()，因为它被改写了
            if (Page.class.getName().equals(mappedStatement.getConfiguration().getResultMap(((ResultMap) ((List) mappedStatement.getResultMaps()).get(0)).getId()).getType().getName())
//                    || pageRequestHolder.pageRequest() != null
            ) {
                pageRequestHolder.page(page);
            }
            String originSql = boundSql.getSql().replaceAll("\\s+", " ");
            log.warn("original sql ===> " + originSql);
            int offset = request.getPage() > 0 ? (request.getPage() - 1) * request.getSize() : 0;
            if (request.isCounting()) {
                // 必须先执行PermissionInterceptor添加查询条件，之后PageInterceptor执行时，才是正确的sql，问题是PageInterceptor查询总数时，由于ParameterHandler添加参数时，PageInterceptor(StatementHandler类型)的queryTotal已经执行，且执行的是未添加参数的sql，所以报错
                page.setTotalRecord(this.queryTotal(mappedStatement, boundSql.getParameterMappings(), boundSql.getParameterObject(), originSql));
            }
            String sql = this.buildPageSql(originSql, offset, request.getSize());
            metaObject.setValue("delegate.boundSql.sql", sql);
            log.warn("page     sql ===> " + sql);
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
            statement = connection.prepareStatement(countSql);
            BoundSql countBoundSql = new BoundSql(mappedStatement.getConfiguration(), countSql, parameterMappings, parameterObject);
            new DefaultParameterHandler(mappedStatement, countBoundSql.getParameterObject(), countBoundSql).setParameters(statement);
            long beginTime = System.currentTimeMillis();
            resultSet = statement.executeQuery();
            log.debug("it cost [ " + new BigDecimal(System.currentTimeMillis() - beginTime).divide(new BigDecimal(1000)).setScale(6, RoundingMode.DOWN).doubleValue() + "s ] to execute ===> [" + countSql + "]");
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
        try {
            CCJSqlParserManager parserManager = new CCJSqlParserManager();
            Select select = (Select) parserManager.parse(new StringReader(sql));
            SelectBody selectBody = select.getSelectBody();
            PlainSelect plain = (PlainSelect) selectBody;
            plain.setSelectItems(Arrays.asList(
                    new SelectExpressionItem(CCJSqlParserUtil.parseExpression("count(*) "))
            ));
            plain.setOrderByElements(null);
            return plain.toString();
        } catch (JSQLParserException e) {
            e.printStackTrace();
            return "select count(*) from (" + sql + ") __pager__ ";
        }
    }

    public static Object parseTarget(Object target) {
        MetaObject metaObject = SystemMetaObject.forObject(target);
        while (metaObject.hasGetter("h")) {
            target = metaObject.getValue("h.target");
            metaObject = SystemMetaObject.forObject(target);
        }
        return target;
    }
}