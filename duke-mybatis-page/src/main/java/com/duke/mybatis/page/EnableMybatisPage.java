package com.duke.mybatis.page;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Import(PageAutoConfiguration.class)
public @interface EnableMybatisPage {
}
