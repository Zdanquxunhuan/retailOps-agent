package com.yu.retailops.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({RetailOpsProperties.class, DiagnosisV2Properties.class, DiagnosisV3Properties.class})
public class ApplicationConfiguration { }

