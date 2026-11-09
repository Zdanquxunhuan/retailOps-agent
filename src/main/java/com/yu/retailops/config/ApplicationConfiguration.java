package com.yu.retailops.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RetailOpsProperties.class)
public class ApplicationConfiguration { }
