package com.vladmihalcea.phd.common.config;

import org.springframework.context.annotation.ComponentScan;

/**
 * Adds Spring Rest support.
 *
 * @author Vlad Mihalcea
 */
@ComponentScan(basePackages = {
    "com.vladmihalcea.phd.common.util"
})
public class JmhConfiguration extends SpringWebConfiguration {
}


