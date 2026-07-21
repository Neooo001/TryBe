package com.trybe.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Configuration
public class RestTemplateConfig {
    
    @Bean
    public RestTemplate restTemplate() {
        // 设置连接超时和读取超时
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);  // 5秒连接超时
        factory.setReadTimeout(10000);    // 10秒读取超时
        
        RestTemplate restTemplate = new RestTemplate(factory);

        // 微信API返回的Content-Type为text/plain而非application/json，需要让Jackson兼容text/plain
        restTemplate.getMessageConverters().stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(MappingJackson2HttpMessageConverter.class::cast)
                .forEach(converter -> {
                    List<MediaType> supportedMediaTypes = new ArrayList<>(converter.getSupportedMediaTypes());
                    supportedMediaTypes.add(MediaType.TEXT_PLAIN);
                    converter.setSupportedMediaTypes(supportedMediaTypes);
                });

        // 添加自定义拦截器（如日志、认证等）
        restTemplate.getInterceptors().add(new ClientHttpRequestInterceptor() {
            @Override
            public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                                ClientHttpRequestExecution execution) throws IOException {
                // 可以在这里记录请求日志、添加认证头等
                try {
                    return execution.execute(request, body);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        
        return restTemplate;
    }
}