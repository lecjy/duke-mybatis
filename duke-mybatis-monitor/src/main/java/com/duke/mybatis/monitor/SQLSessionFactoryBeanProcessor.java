//package com.duke.mybatis.monitor;
//
//import org.apache.ibatis.plugin.Interceptor;
//import org.apache.ibatis.plugin.InterceptorChain;
//import org.apache.ibatis.session.Configuration;
//import org.apache.ibatis.session.SqlSessionFactory;
//import org.springframework.beans.BeansException;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.config.BeanPostProcessor;
//import org.springframework.context.ApplicationContext;
//import org.springframework.stereotype.Component;
//import org.springframework.util.CollectionUtils;
//
//import java.lang.reflect.Field;
//import java.util.List;
//
//@Component
//public class SQLSessionFactoryBeanProcessor implements BeanPostProcessor {
//
//    @Autowired
//    private ApplicationContext applicationContext;
//
//    /**
//     * 确保SQLPerformanceInterceptor放在拦截器链的头部
//     */
//    @Override
//    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
//        if (beanName.equals("sqlSessionFactory")) {
//            SqlSessionFactory sqlSessionFactory = (SqlSessionFactory) bean;
//            Configuration configuration = sqlSessionFactory.getConfiguration();
//            try {
//                Field field = configuration.getClass().getDeclaredField("interceptorChain");
//                field.setAccessible(true);
//                InterceptorChain interceptorChain = new InterceptorChain();
//                interceptorChain.addInterceptor(new SQLPerformanceInterceptor());
//                List<Interceptor> interceptors = configuration.getInterceptors();
//                if (!CollectionUtils.isEmpty(interceptors)) {
//                    for (Interceptor interceptor : interceptors) {
//                        interceptorChain.addInterceptor(interceptor);
//                    }
//                }
//                field.set(configuration, interceptorChain); // 将i的值设置为111
//            } catch (NoSuchFieldException | IllegalAccessException e) {
//                e.printStackTrace();
//            }
//        }
//        return bean;
//    }
//}