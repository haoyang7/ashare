/*
 * Copyright (c) 2020 ashare4cloud Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haoyoung.ashare.codegen;

import com.haoyoung.ashare.common.datasource.annotation.EnableDynamicDataSource;
import com.haoyoung.ashare.common.feign.annotation.EnableAshareFeignClients;
import com.haoyoung.ashare.common.security.annotation.EnableAshareResourceServer;
import com.haoyoung.ashare.common.swagger.annotation.EnableAshareDoc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @author lengleng
 * @date 2020/03/11 代码生成模块
 */
@EnableAshareDoc
@EnableDynamicDataSource
@EnableAshareFeignClients
@EnableDiscoveryClient
@SpringBootApplication
@EnableAshareResourceServer
public class AshareCodeGenApplication {

	public static void main(String[] args) {
		SpringApplication.run(AshareCodeGenApplication.class, args);
	}

}