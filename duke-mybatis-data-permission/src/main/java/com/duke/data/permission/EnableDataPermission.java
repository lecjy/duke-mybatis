package com.duke.data.permission;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Import(DataPermissionAutoConfiguration.class)
public @interface EnableDataPermission {
}