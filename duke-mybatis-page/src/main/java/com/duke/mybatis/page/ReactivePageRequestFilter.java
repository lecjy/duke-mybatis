package com.duke.mybatis.page;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class ReactivePageRequestFilter implements WebFilter, PageRequestHolder {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        return chain.filter(exchange).subscriberContext(ctx -> {
//            ctx.put(ReactivePageRequestFilter.PAGE_REQUEST_KEY, request);
            ctx.put(ReactivePageRequestFilter.PAGE_KEY, request);
            return ctx;
        });
    }

    @Override
    public <E> void page(Page<E> page) {
    }

    @Override
    public <E> Page<E> page() {
        return null;
    }

    @Override
    public void remove() {
    }

    //    public static final Class<ServerHttpRequest> PAGE_REQUEST_KEY = ServerHttpRequest.class;
    public static final Class<Page> PAGE_KEY = Page.class;

//    public static Mono<ServerHttpRequest> getRequest() {
//        return Mono.subscriberContext().map(ctx -> ctx.get(PAGE_REQUEST_KEY));
//    }

//    @Override
//    public PageRequest pageRequest() {
//        return getRequest().map(request -> {
//            String page = request.getQueryParams().getFirst("page.page");
//            String size = request.getQueryParams().getFirst("page.size");
//            String counting = request.getQueryParams().getFirst("page.counting");
//            return parse(page, size, counting);
//        }).block();
//    }
}