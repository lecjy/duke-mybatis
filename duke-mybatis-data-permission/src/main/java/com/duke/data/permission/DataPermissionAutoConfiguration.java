package com.duke.data.permission;

import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.List;

public class DataPermissionAutoConfiguration {
    @Autowired
    private UserDataPermissionService permissionUserService;
    @Autowired
    private List<SqlSessionFactory> sqlSessionFactories;

    @PostConstruct
    public void dataPermissionInterceptor() {
        DataPermissionInterceptor dataPermissionInterceptor = new DataPermissionInterceptor(permissionUserService);
        for (SqlSessionFactory sqlSessionFactory : sqlSessionFactories) {
            sqlSessionFactory.getConfiguration().addInterceptor(dataPermissionInterceptor);
        }
    }
    /**
     * 这种方式会造成循环依赖
     */
//    @Bean
//    ConfigurationCustomizer dataPermissionConfigurationCustomizer() {
//        DataPermissionInterceptor dataPermissionInterceptor = new DataPermissionInterceptor(permissionUserService);
//        return configuration -> {
//            configuration.addInterceptor(dataPermissionInterceptor);
//        };
//    }
}