package com.vladmihalcea.phd.common.timing;

import com.vladmihalcea.phd.common.timing.LayerTimings.Layer;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Installs the server-side probes of {@link LayerTimings}: a servlet filter that times every HTTP request,
 * and an advice that times every call of the {@code ForumService} beans, including their transactions.
 *
 * @author Vlad Mihalcea
 */
@Configuration
public class LayerTimingConfiguration {

    /**
     * The first filter of the chain, so the server time covers the whole request processing,
     * from reading the request body to writing the response body.
     */
    @Bean
    public FilterRegistrationBean<Filter> layerTimingFilter() {
        Filter filter = (request, response, chain) -> {
            long begin = LayerTimings.begin();
            try {
                chain.doFilter(request, response);
            } finally {
                LayerTimings.end(Layer.HTTP_SERVER, ((HttpServletRequest) request).getMethod(), begin);
            }
        };
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public static BeanPostProcessor layerTimingServicePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!AopUtils.getTargetClass(bean).getSimpleName().equals("ForumService")) {
                    return bean;
                }
                MethodInterceptor timing = invocation -> {
                    long begin = LayerTimings.begin();
                    try {
                        return invocation.proceed();
                    } finally {
                        LayerTimings.end(Layer.SERVICE, begin);
                    }
                };
                // The first advice of a transactional proxy, so the service time includes the transaction
                if (bean instanceof Advised advised && !advised.isFrozen()) {
                    advised.addAdvice(0, timing);
                    return bean;
                }
                ProxyFactory proxyFactory = new ProxyFactory(bean);
                proxyFactory.setProxyTargetClass(true);
                proxyFactory.addAdvice(timing);
                return proxyFactory.getProxy();
            }
        };
    }
}
