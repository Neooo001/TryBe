package com.trybe.gateway.filters;

import com.trybe.gateway.config.AuthProperties;
import com.trybe.gateway.util.JwtTool;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;


/*
*
* 登录校验过滤器
* */
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final AuthProperties authProperties;
    private final JwtTool jwtTool;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();

        if (isExclude(request.getPath().toString())){
            return chain.filter(exchange);
        }

        String token = null;
        List<String> headers = request.getHeaders().get("authorization");

        if (headers != null && !headers.isEmpty()){
            token = headers.get(0);
            // 去掉 "Bearer " 前缀（前端发送 Authorization: Bearer <token>）
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
        }

        Long userId = null;
        try {
            userId = jwtTool.parseToken(token);
        } catch (Exception e) {
            // 统一返回 HTTP 200 + JSON body（code=301 未登录），前端通过 data.code 判断
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.OK);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String body = "{\"code\":301,\"msg\":\"未登录，请重新登录\",\"data\":null}";
            DataBuffer buffer = response.bufferFactory()
                    .wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        }

        String userInfo = userId.toString();
        ServerWebExchange swe = exchange.mutate()
                .request(builder -> builder.header("user-info", userInfo))
                .build();

        return chain.filter(swe);
    }

    private boolean isExclude(String path) {
        for (String pathPattern : authProperties.getExcludePaths()) {
            if (antPathMatcher.match(pathPattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
