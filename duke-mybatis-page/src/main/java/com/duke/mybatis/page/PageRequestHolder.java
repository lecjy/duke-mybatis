package com.duke.mybatis.page;

import java.util.regex.Pattern;

public interface PageRequestHolder {
    Pattern pattern = Pattern.compile("^[0-9]*$");

//    PageRequest pageRequest();

    <E> void page(Page<E> page);

    <E> Page<E> page();

    void remove();

//    default PageRequest parse(String page, String size, String counting) {
//        if (page != null && size != null && pattern.matcher(page).matches() && pattern.matcher(size).matches()) {
//            return PageRequest.page(Integer.valueOf(page), Integer.valueOf(size), Boolean.parseBoolean(counting));
//        }
//        return null;
//    }
}
