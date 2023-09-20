package com.duke.mybatis.page;

import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

public class PageAutoConfiguration {
    @Autowired
    @Lazy
    private PageRequestHolder pageRequestHolder;

    @Bean
    ConfigurationCustomizer mybatisPageConfigurationCustomizer() {
        StatementPageInterceptor pageInterceptor = new StatementPageInterceptor(pageRequestHolder);
//        ExecutorPageInterceptor pageInterceptor = new ExecutorPageInterceptor(pageRequestHolder);
        return configuration -> {
            configuration.setObjectFactory(new PageObjectFactory(pageRequestHolder));
            configuration.setObjectWrapperFactory(new PageObjectWrapperFactory(pageRequestHolder));
            configuration.addInterceptor(pageInterceptor);
            configuration.addInterceptor(new ResultSetInterceptor());
        };
    }

    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @Bean
    PageRequestHolder reactivePageRequestHolder() {
        return new ReactivePageRequestFilter();
    }

    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @Bean
    PageRequestHolder mvcPageRequestHolder() {
        return new MvcPageRequestHolder();
    }
}
/*
@Component
public class SqlSessionFactoryBeanPostProcessor implements BeanPostProcessor {

    @Resource
    private PageObjectWrapperFactory objectWrapperFactory;

    @Resource
    public PageObjectFactory objectFactory;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof SqlSessionFactoryBean factoryBean) {
            factoryBean.setObjectFactory(objectFactory);
            factoryBean.setObjectWrapperFactory(objectWrapperFactory);
            PageInterceptor pageInterceptor = new PageInterceptor();
            Properties properties = new Properties();
            pageInterceptor.setProperties(properties);
            factoryBean.setPlugins(new PageInterceptor(), new DataPermissionInterceptor());
        }
        return bean;
    }
}
*/