package com.duke.mybatis.monitor;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.InterceptorChain;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.util.List;


@Configuration
public class SQLMonitorAutoConfiguration {

    /**
     * 确保SQLPerformanceInterceptor放在拦截器链的头部
     */
    @Bean
    ConfigurationCustomizer monitorConfigurationCustomizer() {
        SQLPerformanceInterceptor sqlPerformanceInterceptor = new SQLPerformanceInterceptor();
        return configuration -> {
            try {
                Field field = configuration.getClass().getDeclaredField("interceptorChain");
                field.setAccessible(true);
                InterceptorChain interceptorChain = new InterceptorChain();
                interceptorChain.addInterceptor(sqlPerformanceInterceptor);
                List<Interceptor> interceptors = configuration.getInterceptors();
                if (!CollectionUtils.isEmpty(interceptors)) {
                    for (Interceptor interceptor : interceptors) {
                        interceptorChain.addInterceptor(interceptor);
                    }
                }
                field.set(configuration, interceptorChain);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        };
    }
}
