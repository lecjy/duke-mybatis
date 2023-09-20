package com.duke.mybatis.page;

import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.CallableStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Intercepts({
        @Signature(type = ResultSetHandler.class, method = "handleResultSets", args = {Statement.class}),
        @Signature(type = ResultSetHandler.class, method = "handleCursorResultSets", args = {Statement.class}),
        @Signature(type = ResultSetHandler.class, method = "handleOutputParameters", args = {CallableStatement.class})
})
public class ResultSetInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof DefaultResultSetHandler handler) {
            MetaObject metaObject = SystemMetaObject.forObject(handler);
            MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("mappedStatement");
            Configuration configuration = (Configuration) metaObject.getValue("configuration");
            MetaObject resultMapsMeta = SystemMetaObject.forObject(mappedStatement);
            List<ResultMap> list = (List<ResultMap>) resultMapsMeta.getValue("resultMaps");
            List<ResultMap> newResultMaps = new ArrayList<>(list.size());
            Iterator<ResultMap> iterator = list.iterator();
            while (iterator.hasNext()) {
                ResultMap next = iterator.next();
                if (Page.class.equals(next.getType())) {
                    String statementId = next.getId().split("-")[0];
                    int index = statementId.lastIndexOf(".");
                    String className = statementId.substring(0, index);
                    String methodName = statementId.substring(index + 1);
                    Class returnType = returnType(configuration, className, methodName);
                    if (returnType != null) {
                        next = new ResultMap.Builder(configuration, next.getId(), returnType, next.getResultMappings(), next.getAutoMapping()).build();
                    }
                }
                newResultMaps.add(next);
            }
            // 如果使用StatementPageInterceptor，这里替换为真实类型后，ObjectWrapper就无法获取到Page类型，所以使用ExecutorPageInterceptor
            resultMapsMeta.setValue("resultMaps", newResultMaps);
        }
        return target;
//            target = new PageResultSetHandler((Executor) metaObject.getValue("executor"),
//                    (MappedStatement) metaObject.getValue("mappedStatement"),
//                    (ParameterHandler) metaObject.getValue("parameterHandler"),
//                    (ResultHandler<?>) metaObject.getValue("resultHandler"),
//                    (BoundSql) metaObject.getValue("boundSql"),
//                    (RowBounds) metaObject.getValue("rowBounds"));
//            return Interceptor.super.plugin(target);
    }

    public static Class returnType(Configuration configuration, String className, String methodName) {
        Map<String, ? extends Class<?>> mappers = configuration.getMapperRegistry().getMappers().stream().collect(Collectors.toMap(Class::getName, item -> item));
        Class<?> clazz = mappers.get(className);
        Method[] declaredMethods = clazz.getDeclaredMethods();
        for (Method method : declaredMethods) {
            Type genericReturnType = method.getGenericReturnType();
            if (methodName.equals(method.getName()) && genericReturnType instanceof ParameterizedType parameterizedType) {
                if (Page.class.equals(parameterizedType.getRawType())) {
                    Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                    return (Class) actualTypeArguments[0];
                }
            }
        }
        return null;
    }
}
