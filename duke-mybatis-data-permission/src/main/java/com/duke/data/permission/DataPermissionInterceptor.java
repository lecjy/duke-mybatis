package com.duke.data.permission;

import com.duke.common.base.utils.CollectionUtils;
import com.duke.common.base.utils.StringUtils;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Intercepts({
        // StatemenHandler用于向SQL中添加数据权限对应的查询条件，而查询条件中可能带有参数，处理参数占位符的是ParameterHandler
        // 在使用分页拦截器时，为保证查询结果正确，即分页的SQl中包含了数据权限对应的查询条件，就不能使用Executor进行分页，因为MyBatis中Executor拦截器比StatementHandler先执行
        // 所以使用DataPermissionInterceptor时，分页拦截器使用的StatementHandler，它和这里的StatementHandler作用是不一样的，它用于分页，这里的StatementHandler用于向SQL中添加数据权限对应的查询条件
        @Signature(type = ParameterHandler.class, method = "setParameters", args = {PreparedStatement.class}),
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class DataPermissionInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(DataPermissionInterceptor.class);
    private static final Pattern PATTERN = Pattern.compile("[$#]\\{(.*?)\\.?(.*?)}", Pattern.CASE_INSENSITIVE);
    Map<String, Map<String, Method>> map = new HashMap<>();

    private UserDataPermissionService userDataPermissionService;

    public DataPermissionInterceptor(UserDataPermissionService userDataPermissionService) {
        this.userDataPermissionService = userDataPermissionService;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object target = invocation.getTarget();
        if (target instanceof StatementHandler) {
            StatementHandler statementHandler = (StatementHandler) parseTarget(target);
            // 通过MetaObject访问对象的属性
            MetaObject metaObject = MetaObject.forObject(
                    statementHandler,
                    SystemMetaObject.DEFAULT_OBJECT_FACTORY,
                    SystemMetaObject.DEFAULT_OBJECT_WRAPPER_FACTORY,
                    new DefaultReflectorFactory());
            // 获取成员变量mappedStatement
            MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
            SqlCommandType sqlCommandType = mappedStatement.getSqlCommandType();
            if (!SqlCommandType.SELECT.name().equals(sqlCommandType.name())) {
                return invocation.proceed();
            }
            String id = mappedStatement.getId();
            String className = id.substring(0, id.lastIndexOf('.'));
            String methodName = id.substring(id.lastIndexOf('.') + 1);
            List<PermissionExpression> permits = null;
            parseMethods(className);
            Method method = map.get(className).get(methodName);
            DataPermission annotation = method.getAnnotation(DataPermission.class);
            if (annotation != null) {
                permits = userDataPermissionService.dataPermissions();
            }
            if (CollectionUtils.isNotEmpty(permits)) {
                String originSql = ((String) metaObject.getValue("delegate.boundSql.sql")).replaceAll("\\s+", " ");
                CCJSqlParserManager parserManager = new CCJSqlParserManager();
                Select select = (Select) parserManager.parse(new StringReader(originSql));
                log.warn("original sql ===> " + originSql);
                appendPermissionCondition(select.getSelectBody(), permits);
                log.warn("sql with permission condition ===> " + select.toString());
                metaObject.setValue("delegate.boundSql.sql", select.toString());
            }
        }
        if (target instanceof ParameterHandler) {
            ParameterHandler parameterHandler = (ParameterHandler) invocation.getTarget();
            MetaObject metaObject = MetaObject.forObject(parameterHandler, SystemMetaObject.DEFAULT_OBJECT_FACTORY, SystemMetaObject.DEFAULT_OBJECT_WRAPPER_FACTORY, new DefaultReflectorFactory());
            MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("mappedStatement");
            String id = mappedStatement.getId();
            String className = id.substring(0, id.lastIndexOf('.'));
            String methodName = id.substring(id.lastIndexOf('.') + 1);
            List<PermissionExpression> permits = null;
            parseMethods(className);
            Method method = map.get(className).get(methodName);
            DataPermission annotation = method.getAnnotation(DataPermission.class);
            if (annotation != null) {
                permits = userDataPermissionService.dataPermissions();
            }
            if (CollectionUtils.isNotEmpty(permits)) {
                List<PermissionExpression> collect = permits.parallelStream().filter(item -> {
                    String expression = item.getExpression();
                    Matcher matcher = PATTERN.matcher(expression);
                    boolean match = matcher.find();
                    if (match) {
                        String paramExpression = matcher.group(0);
                        String[] split = paramExpression.replace("#{", "").replace("}", "").split("\\.");
                        String prefix = "";
                        String name = split[0];
                        if (split.length > 1) {
                            prefix = name;
                            name = split[1];
                        }
                        item.setParsedExpression(new ParsedExpression(prefix, name));
                        item.setExpression(expression.replace(paramExpression, "?"));
                    }
                    return match;
                }).toList();
                PreparedStatement preparedStatement = (PreparedStatement) invocation.getArgs()[0];
                BoundSql boundSql = (BoundSql) metaObject.getValue("boundSql");
                //添加参数
                if (CollectionUtils.isNotEmpty(collect)) {
                    Configuration conf = (Configuration) metaObject.getValue("configuration");
                    // Mapper传入的参数
                    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
                    // Mybatis解析后的参数
                    Object parameterObject = parameterHandler.getParameterObject();
                    MetaObject parameter = SystemMetaObject.forObject(parameterObject);
                    for (PermissionExpression permission : collect) {
                        ParsedExpression parsedExpression = permission.getParsedExpression();
                        String property = parsedExpression.getPrefix() + "." + parsedExpression.getName();
                        parameterMappings.add(new ParameterMapping.Builder(conf, property, parameter.getValue(property).getClass()).build());
                        if (parameterObject instanceof Map) {
                            MapperMethod.ParamMap paramMap = (MapperMethod.ParamMap) parameterObject;
                            paramMap.put("param" + (paramMap.size() / 2 + 1), parameter.getValue(property));
                        }
                    }
                }
                parameterHandler.setParameters(preparedStatement);
            }
        }
        return invocation.proceed();
    }

    public static Object parseTarget(Object target) {
        MetaObject metaObject = SystemMetaObject.forObject(target);
        while (metaObject.hasGetter("h")) {
            target = metaObject.getValue("h.target");
            metaObject = SystemMetaObject.forObject(target);
        }
        return target;
    }

    static void appendPermissionCondition(SelectBody body, List<PermissionExpression> permits) throws JSQLParserException {
        // 简单查询
        if (body instanceof PlainSelect) {
            PlainSelect plain = (PlainSelect) body;
            List<SelectItem> selectItems = plain.getSelectItems();
            if (CollectionUtils.isNotEmpty(selectItems)) {
                select(selectItems, permits);
            }
            from(plain, permits);
            // union查询
        } else if (body instanceof SetOperationList) {
            SetOperationList list = (SetOperationList) body;
            List<SelectBody> selects = list.getSelects();
            for (SelectBody selectBody : selects) {
                appendPermissionCondition(selectBody, permits);
            }
        }
    }

    private static void select(List<SelectItem> selectItems, List<PermissionExpression> permits) throws JSQLParserException {
        for (SelectItem selectItem : selectItems) {
            if (!(selectItem instanceof SelectExpressionItem)) {
                continue;
            }
            SelectExpressionItem expressionItem = (SelectExpressionItem) selectItem;
            Expression expression = expressionItem.getExpression();
            if (expression instanceof SubSelect) {
                SubSelect subSelect = (SubSelect) expression;
                SelectBody selectBody = subSelect.getSelectBody();
                appendPermissionCondition(selectBody, permits);
            }
        }
    }

    public static void from(PlainSelect plain, List<PermissionExpression> permits) throws JSQLParserException {
        FromItem fromItem = plain.getFromItem();
        expression(plain.getWhere(), permits);
        // 简单查询
        if (fromItem instanceof Table) {
            doAppendCondition((Table) fromItem, plain, permits);
            List<Join> joins = plain.getJoins();
            if (CollectionUtils.isNotEmpty(joins)) {
                for (Join join : joins) {
                    FromItem rightItem = join.getRightItem();
                    if (rightItem instanceof Table) {
                        doAppendCondition((Table) rightItem, plain, permits);
                    }
                }
            }
            // 子查询
        } else if (fromItem instanceof SubSelect) {
            SubSelect subSelect = (SubSelect) fromItem;
            SelectBody selectBody = subSelect.getSelectBody();
            appendPermissionCondition(selectBody, permits);
        }
    }

    public static void doAppendCondition(Table fromTable, PlainSelect plain, List<PermissionExpression> permits) throws JSQLParserException {
        String tableName = fromTable.getName();
        String tableAlias = "";
        Alias alias = fromTable.getAlias();
        if (alias != null) {
            tableAlias = alias.getName();
        }

        List<String> and = new ArrayList<>();
        List<String> or = new ArrayList<>();

        String finalTableAlias = tableAlias;
        permits.stream().filter(item -> item.getTableName().trim().equals(tableName)).filter(item -> item.getGroup().trim().equals("and")).forEach(item -> {
            String expression = item.getExpression().trim();
            Matcher matcher = PATTERN.matcher(expression);
            if (matcher.find()) {
                and.add(expression.replace(matcher.group(0), "?"));
            } else {
                and.add((StringUtils.isNotEmpty(finalTableAlias) ? finalTableAlias + "." : "") + expression);
            }
        });
        permits.stream().filter(item -> item.getTableName().trim().equals(tableName)).filter(item -> item.getGroup().trim().equals("or")).forEach(item -> {
            String expression = item.getExpression().trim();
            Matcher matcher = PATTERN.matcher(expression);
            if (matcher.find()) {
                or.add(expression.replace(matcher.group(0), "?"));
            } else {
                or.add((StringUtils.isNotEmpty(finalTableAlias) ? finalTableAlias + "." : "") + expression);
            }
        });

        Expression where = plain.getWhere();
        if (where == null) {
            if (CollectionUtils.isNotEmpty(and)) {
                plain.setWhere(CCJSqlParserUtil.parseCondExpression(String.join(" and ", and) + (CollectionUtils.isEmpty(or) ? "" : or.size() == 1 ? " and " + String.join(" ", or) : " and (" + String.join(" or ", or) + ") ")));
            } else if (CollectionUtils.isNotEmpty(or)) {
                plain.setWhere(CCJSqlParserUtil.parseCondExpression(String.join(" or ", or)));
            }
        } else {
            if (CollectionUtils.isNotEmpty(and)) {
                plain.setWhere(CCJSqlParserUtil.parseCondExpression("(" + where + ") and " + String.join(" and ", and) + (CollectionUtils.isEmpty(or) ? "" : or.size() == 1 ? " and " + String.join(" ", or) : " and (" + String.join(" or ", or) + ") ")));
            } else if (CollectionUtils.isNotEmpty(or)) {
                plain.setWhere(CCJSqlParserUtil.parseCondExpression("(" + where + ") and " + (or.size() == 1 ? String.join(" ", or) : " (" + String.join(" or ", or) + ") ")));
            }
        }
    }

    public static void expression(Expression expression, List<PermissionExpression> permits) throws JSQLParserException {
        if (expression == null) {
            return;
        }
        // a或者x.m就是column，1就是LongValue
        if (!(expression instanceof Column ||
                expression instanceof LongValue ||
                expression instanceof StringValue ||
                expression instanceof HexValue ||
                expression instanceof DoubleValue ||
                expression instanceof DateValue)) {
        }
        // AndExpression/InExpression/EqualsTo/NotEqualsTo/MinorThan/MinorThanEquals/GreaterThan/GreaterThanEquals
        if (expression instanceof BinaryExpression) {// AndExpression or InExpression
            BinaryExpression binaryExpression = (BinaryExpression) expression;
            expression(binaryExpression.getRightExpression(), permits);
            expression(binaryExpression.getLeftExpression(), permits);
        } else if (expression instanceof Function) {
            Function function = (Function) expression;
        } else if (expression instanceof SubSelect) {
            SubSelect subSelect = (SubSelect) expression;
            appendPermissionCondition(subSelect.getSelectBody(), permits);
        }
        // TODO between
    }

    public static void jsqlParse(String sql) throws JSQLParserException {
        CCJSqlParserManager parserManager = new CCJSqlParserManager();
        Select select = (Select) parserManager.parse(new StringReader(sql));
        SelectBody selectBody = select.getSelectBody();
        PlainSelect plain = (PlainSelect) selectBody;

        plain.setSelectItems(Arrays.asList(
                new SelectExpressionItem(CCJSqlParserUtil.parseExpression("count(*) "))
        ));

//        appendPermissionCondition(selectBody, Collections.emptyList());
    }

    public static void main(String[] args) {
        try {
            jsqlParse("select sum(actual), (select 1 from test), sum(plan) from (" +
                    "    select IFNULL(sum(IFNULL(a.count * a.unit_price, 0)), 0) actual, 0 plan " +
                    "        from t_bom a left join t_project b on a.project_id = b.id " +
                    "        where task_id = 1 and a.project_id = b.id " +
                    "    union all " +
                    "        select 0 actual, IFNULL(sum(IFNULL(plan_total, 0)), 0) plan " +
                    "        from t_project_task " +
                    "        where a in (select 1,2,3 from test_a where 1 = 1) " +
                    "        and id = 1 " +
                    "        and a in (select 2,3,4 from test) " +
                    "        union select 1 from m where 1 = '1' or 1 = 2" +
                    "        union select 2 from n where y in (1,2,3) " +
                    "        union select 3 from p where j > '2020-01-03' " +
                    "        union select 3 from p where k < 4 " +
                    "        union select 3 from p where k <= 0x11 " +
                    "        union select 3 from p where k >= 4.2 " +
                    "        union select 3 from p where x not in (12,3) " +
                    "        union select 3 from p where k is not null " +
                    "        union select 3 from p where exist (select 1 from n)" +
                    ") x");
        } catch (JSQLParserException e) {
            e.printStackTrace();
        }
    }

    private void parseMethods(String className) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(className);
        if (!map.containsKey(className)) {
            map.put(className, new HashMap<>());
        }
        for (Method method : clazz.getDeclaredMethods()) {
            map.get(className).put(method.getName(), method);
        }
    }
}
